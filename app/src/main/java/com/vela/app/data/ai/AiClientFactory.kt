package com.vela.app.data.ai

import com.vela.app.data.model.UserPreferences

/**
 * 根据用户配置构建 AI 客户端；未配置时统一降级为 Unavailable 实现。
 */
class AiClientFactory {
    fun extractionClient(preferences: UserPreferences): AiExtractionClient =
        preferences.takeIf { it.hasTextService() }?.let {
            HttpAiExtractionClient(
                endpoint = it.aiEndpoint,
                apiKey = it.aiApiKey,
                textModel = it.aiTextModel,
                visionModel = it.aiVisionModel,
            )
        } ?: UnavailableAiExtractionClient

    fun adviceClient(preferences: UserPreferences): AiEventAdviceClient =
        preferences.takeIf { it.hasTextService() }?.let {
            HttpAiEventAdviceClient(
                endpoint = it.aiEndpoint,
                apiKey = it.aiApiKey,
                model = it.aiTextModel,
            )
        } ?: UnavailableAiEventAdviceClient

    fun voiceClient(preferences: UserPreferences): VoiceTranscriptionClient =
        preferences.takeIf { it.aiEndpoint.isNotBlank() && it.aiVoiceModel.isNotBlank() }?.let {
            HttpVoiceTranscriptionClient(
                endpoint = it.aiEndpoint,
                apiKey = it.aiApiKey,
                model = it.aiVoiceModel,
            )
        } ?: UnavailableVoiceTranscriptionClient

    private fun UserPreferences.hasTextService(): Boolean =
        aiEndpoint.isNotBlank() && aiTextModel.isNotBlank()
}
