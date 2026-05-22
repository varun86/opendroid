package com.opendroid.ai.core.llm

import com.opendroid.ai.core.llm.providers.*
import com.opendroid.ai.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LLMProviderFactory @Inject constructor(
    private val claudeProvider: Provider<ClaudeProvider>,
    private val openAIProvider: Provider<OpenAIProvider>,
    private val geminiProvider: Provider<GeminiProvider>,
    private val mistralProvider: Provider<MistralProvider>,
    private val groqProvider: Provider<GroqProvider>,
    private val ollamaProvider: Provider<OllamaProvider>,
    private val openRouterProvider: Provider<OpenRouterProvider>,
    private val togetherAIProvider: Provider<TogetherAIProvider>,
    private val cohereProvider: Provider<CohereProvider>,
    private val deepSeekProvider: Provider<DeepSeekProvider>,
    private val copilotProvider: Provider<CopilotProvider>,
    private val settingsRepository: SettingsRepository
) {

    fun getProviderByName(name: String): LLMProvider {
        return when (name) {
            "Anthropic Claude" -> claudeProvider.get()
            "OpenAI" -> openAIProvider.get()
            "Google Gemini" -> geminiProvider.get()
            "Mistral AI" -> mistralProvider.get()
            "Groq" -> groqProvider.get()
            "Ollama" -> ollamaProvider.get()
            "OpenRouter" -> openRouterProvider.get()
            "Together AI" -> togetherAIProvider.get()
            "Cohere" -> cohereProvider.get()
            "DeepSeek" -> deepSeekProvider.get()
            "Copilot API" -> copilotProvider.get()
            else -> geminiProvider.get()
        }
    }

    private fun getFallbackChain(primaryName: String): List<LLMProvider> {
        val providersList = listOf(
            "Google Gemini",
            "OpenAI",
            "Anthropic Claude",
            "Groq",
            "Mistral AI",
            "OpenRouter",
            "Together AI",
            "Cohere",
            "DeepSeek",
            "Copilot API",
            "Ollama"
        )
        val orderedNames = mutableListOf<String>()
        orderedNames.add(primaryName)
        providersList.forEach { name ->
            if (name != primaryName) orderedNames.add(name)
        }
        return orderedNames.map { getProviderByName(it) }
    }

    suspend fun getActiveProvider(): LLMProvider {
        val config = settingsRepository.llmConfig.first()
        val chain = getFallbackChain(config.activeProvider)
        for (provider in chain) {
            if (provider.isAvailable()) {
                return provider
            }
        }
        // If nothing is configured, default to Gemini (it has Nano offline mock fallback)
        return geminiProvider.get()
    }

    suspend fun executeWithFallback(request: LLMRequest): LLMResponse {
        val config = settingsRepository.llmConfig.first()
        val chain = getFallbackChain(config.activeProvider)
        val errors = mutableListOf<String>()

        for (provider in chain) {
            if (provider.isAvailable()) {
                try {
                    val response = provider.complete(request)
                    // Benchmark successfully executed provider in settings background
                    updateLatencyBenchmark(provider.name, response.latencyMs)
                    return response
                } catch (e: Exception) {
                    errors.add("${provider.name}: ${e.localizedMessage}")
                }
            }
        }
        throw IllegalStateException("All available LLM providers failed execution:\n" + errors.joinToString("\n"))
    }

    private suspend fun updateLatencyBenchmark(providerName: String, latency: Long) {
        settingsRepository.updateConfig { current ->
            val updatedBenchmarks = current.latencyBenchmarks.toMutableMap()
            updatedBenchmarks[providerName] = latency
            current.copy(latencyBenchmarks = updatedBenchmarks)
        }
    }
}
