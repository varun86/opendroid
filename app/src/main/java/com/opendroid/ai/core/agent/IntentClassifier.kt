package com.opendroid.ai.core.agent

import com.opendroid.ai.core.llm.LLMProviderFactory
import com.opendroid.ai.core.llm.LLMRequest
import com.opendroid.ai.core.llm.ResponseFormat
import com.opendroid.ai.data.models.ChatMessage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentClassifier @Inject constructor(
    private val llmProviderFactory: LLMProviderFactory
) {
    suspend fun requiresAction(query: String): Boolean {
        // Broad local heuristic check for action words
        val actionKeywords = listOf(
            "open", "launch", "start", "turn", "toggle", "enable", "disable", "set", "lock", "restart",
            "take", "record", "send", "make", "play", "pause", "resume", "next", "prev", "order", "search",
            "pay", "check", "split", "run", "create", "schedule", "list", "read", "write", "delete", "click",
            "type", "scroll", "get", "show", "whatsapp", "call", "sms", "email", "alarm", "timer", "reminder",
            "note", "notes", "calendar", "weather", "news", "flashlight", "flash", "wifi", "bluetooth",
            "brightness", "volume", "screenshot", "dnd", "mute", "unmute"
        )
        val hasActionKeyword = actionKeywords.any { query.contains(it, ignoreCase = true) }

        return try {
            val provider = llmProviderFactory.getActiveProvider()
            val prompt = """
                Classify the user's intent: "$query".
                Does this request require executing one or more device/app actions (e.g. opening an app, toggling a setting like flashlight/wifi/bluetooth, setting volume/brightness, sending a message/email, making a call, setting an alarm/timer/reminder, playing music, booking a ride, checking weather/news, paying via UPI, etc.)?
                Return strictly "ACTION" if it requires executing an action, or "CONVERSATIONAL" if it is a general chat, question, or statement that can be answered directly with a conversational text response.
            """.trimIndent()

            val response = provider.complete(
                LLMRequest(
                    systemPrompt = "You are an intent classification routing helper.",
                    messages = listOf(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            text = prompt,
                            sender = ChatMessage.Sender.USER
                        )
                    ),
                    temperature = 0.0f,
                    maxTokens = 5,
                    responseFormat = ResponseFormat.TEXT
                )
            )
            response.content.contains("ACTION", ignoreCase = true)
        } catch (e: Exception) {
            // Fallback to keyword heuristics if LLM is unreachable or offline
            hasActionKeyword
        }
    }
}
