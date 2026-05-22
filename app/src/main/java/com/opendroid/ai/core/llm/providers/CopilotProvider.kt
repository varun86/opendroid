package com.opendroid.ai.core.llm.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.opendroid.ai.core.llm.*
import com.opendroid.ai.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CopilotProvider @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : LLMProvider {

    override val name: String = "Copilot API"
    override val availableModels: List<String> = listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun complete(request: LLMRequest): LLMResponse {
        val config = settingsRepository.llmConfig.first()
        val rawUrl = config.copilotUrl.trim()
        val baseUrl = if (rawUrl.isNotEmpty()) rawUrl else "http://10.0.2.2:4141"
        val endpoint = when {
            baseUrl.endsWith("/v1/chat/completions") || baseUrl.endsWith("/chat/completions") -> baseUrl
            baseUrl.endsWith("/v1") -> "$baseUrl/chat/completions"
            else -> "$baseUrl/v1/chat/completions"
        }

        val startTime = System.currentTimeMillis()

        // Build messages payload
        val messagesList = mutableListOf<Map<String, String>>()
        messagesList.add(mapOf("role" to "system", "content" to request.systemPrompt))
        request.messages.forEach { msg ->
            val role = if (msg.sender == com.opendroid.ai.data.models.ChatMessage.Sender.USER) "user" else "assistant"
            messagesList.add(mapOf("role" to role, "content" to msg.text))
        }

        val requestBodyMap = mutableMapOf<String, Any>(
            "model" to config.activeModel,
            "messages" to messagesList,
            "temperature" to request.temperature,
            "max_tokens" to request.maxTokens
        )

        if (request.responseFormat == ResponseFormat.JSON) {
            requestBodyMap["response_format"] = mapOf("type" to "json_object")
        }

        val bodyJson = gson.toJson(requestBodyMap)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toRequestBody(mediaType))

        val apiKey = config.apiKeys[name]
        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Copilot API request failed: Code ${response.code} - ${response.body?.string()}")
            }
            val responseBody = response.body?.string() ?: throw IOException("Empty response body from Copilot API")
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
            val messageObj = choices[0].asJsonObject.getAsJsonObject("message")
            val content = messageObj.get("content").asString

            val usage = jsonResponse.getAsJsonObject("usage")
            val tokensUsed = usage?.get("total_tokens")?.asInt ?: 0

            return LLMResponse(
                content = content,
                tokensUsed = tokensUsed,
                model = config.activeModel,
                provider = name,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    override fun streamComplete(request: LLMRequest): Flow<String> = flow {
        try {
            val response = complete(request)
            val words = response.content.split(" ")
            for (word in words) {
                emit("$word ")
                kotlinx.coroutines.delay(50)
            }
        } catch (e: Exception) {
            emit("Error streaming Copilot API: ${e.localizedMessage}")
        }
    }

    override suspend fun isAvailable(): Boolean {
        return true
    }
}
