package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.retrievalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

enum class AiChatMode(
    val label: String,
    val modelName: String,
    val subtitle: String,
    val usesMapsGrounding: Boolean
) {
    MOSQUE_MAPS(
        label = "Mosque & Maps",
        modelName = "gemini-3.8-flash",
        subtitle = "Google Maps grounded nearby places",
        usesMapsGrounding = true
    ),
    ISLAMIC_COMPANION(
        label = "Islamic Guide",
        modelName = "gemini-3.8-flash",
        subtitle = "Quran, Hadith & Solat reflection",
        usesMapsGrounding = false
    ),
    QUICK_STUDY(
        label = "Quick Study",
        modelName = "gemini-3.5-flash-lite",
        subtitle = "Fast duas, meanings & dhikr Q&A",
        usesMapsGrounding = false
    )
}

data class GroundedSourceLink(
    val title: String,
    val uri: String,
    val isMapsPlace: Boolean,
    val detail: String = ""
)

data class AiStreamChunk(
    val textDelta: String,
    val sources: List<GroundedSourceLink>
)

class NoorAiRepository {

    private val defaultCompanionModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiChatMode.ISLAMIC_COMPANION.modelName,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = buildSystemInstruction(
                mode = AiChatMode.ISLAMIC_COMPANION,
                locationLabel = "Global"
            )
        )
    }

    private val defaultFastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiChatMode.QUICK_STUDY.modelName,
            systemInstruction = buildSystemInstruction(
                mode = AiChatMode.QUICK_STUDY,
                locationLabel = "Global"
            )
        )
    }

    fun startChatSession(
        mode: AiChatMode,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        history: List<Content> = emptyList()
    ): Chat {
        val model = createModelForMode(
            mode = mode,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel
        )
        return model.startChat(history = history)
    }

    fun streamChatReply(
        chat: Chat,
        userMessage: String
    ): Flow<AiStreamChunk> {
        return chat.sendMessageStream(userMessage)
            .map { response ->
                AiStreamChunk(
                    textDelta = response.text.orEmpty(),
                    sources = extractGroundedSources(response)
                )
            }
            .catch { error ->
                if (error is CancellationException && error !is TimeoutCancellationException) {
                    throw error
                }
                Log.e(TAG, "Chat stream error", error)
                throw error
            }
    }

    suspend fun generateMapsMosqueGuide(
        prompt: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        onChunk: (String, List<GroundedSourceLink>) -> Unit
    ): Result<Pair<String, List<GroundedSourceLink>>> {
        val accumulated = StringBuilder()
        val collectedSources = linkedMapOf<String, GroundedSourceLink>()
        return try {
            val model = createModelForMode(
                mode = AiChatMode.MOSQUE_MAPS,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel
            )
            withTimeout(AI_TIMEOUT_MS) {
                model.generateContentStream(prompt).collect { response ->
                    val delta = response.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                    }
                    extractGroundedSources(response).forEach { src ->
                        collectedSources[src.uri] = src
                    }
                    onChunk(accumulated.toString(), collectedSources.values.toList())
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isBlank()) {
                Result.failure(IllegalStateException("Gemini returned an empty response."))
            } else {
                Result.success(finalText to collectedSources.values.toList())
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Maps AI guide timed out", e)
            Result.failure(IllegalStateException("Request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Maps AI guide failed", e)
            Result.failure(e)
        }
    }

    private fun createModelForMode(
        mode: AiChatMode,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): GenerativeModel {
        return when (mode) {
            AiChatMode.MOSQUE_MAPS -> {
                Firebase.ai.generativeModel(
                    modelName = mode.modelName,
                    tools = listOf(Tool.googleMaps()),
                    toolConfig = ToolConfig(
                        retrievalConfig = retrievalConfig {
                            latLng = LatLng(latitude, longitude)
                            languageCode = "en"
                        }
                    ),
                    systemInstruction = buildSystemInstruction(mode, locationLabel)
                )
            }
            AiChatMode.ISLAMIC_COMPANION -> defaultCompanionModel
            AiChatMode.QUICK_STUDY -> defaultFastModel
        }
    }

    private fun extractGroundedSources(response: GenerateContentResponse): List<GroundedSourceLink> {
        val metadata = response.candidates.firstOrNull()?.groundingMetadata ?: return emptyList()
        val links = mutableListOf<GroundedSourceLink>()
        metadata.groundingChunks.forEach { chunk ->
            chunk.maps?.let { mapsChunk ->
                val uri = mapsChunk.uri?.takeIf { it.isNotBlank() } ?: return@let
                val title = mapsChunk.title?.takeIf { it.isNotBlank() } ?: "Google Maps Place"
                links += GroundedSourceLink(
                    title = title,
                    uri = uri,
                    isMapsPlace = true
                )
            }
            chunk.web?.let { webChunk ->
                val uri = webChunk.uri?.takeIf { it.isNotBlank() } ?: return@let
                val title = webChunk.title?.takeIf { it.isNotBlank() } ?: webChunk.domain ?: "Web Source"
                links += GroundedSourceLink(
                    title = title,
                    uri = uri,
                    isMapsPlace = false,
                    detail = webChunk.domain.orEmpty()
                )
            }
        }
        return links.distinctBy { it.uri }
    }

    private fun buildSystemInstruction(mode: AiChatMode, locationLabel: String): Content {
        val guidance = when (mode) {
            AiChatMode.MOSQUE_MAPS ->
                "You are Noor's Mosque & Ibadah Place Finder assistant powered by Google Maps grounding. " +
                    "The user's active area is $locationLabel. Help users find nearby mosques (masjid), suraus, " +
                    "musallas, airport/mall prayer rooms, Jumu'ah Friday prayer spots, and wudu or women's prayer areas. " +
                    "Be accurate, concise, and clear about distances, landmarks, and accessibility using grounded Maps results."
            AiChatMode.ISLAMIC_COMPANION ->
                "You are Noor, a calm, scholarly, and respectful Islamic spiritual companion. " +
                    "Answer questions on Quran verses, Tafsir, authentic Hadith, Solat timings and travel prayers (Jamak/Qasar), " +
                    "and daily Dhikr/Salawat reflection. Cite Surah/Ayah numbers or Hadith collections when relevant, acknowledge " +
                    "scholarly differences gently, and advise consulting qualified local scholars or official religious authorities " +
                    "(such as JAKIM Malaysia) for formal fatwa rulings."
            AiChatMode.QUICK_STUDY ->
                "You are Noor Quick Study, a concise and clear assistant for short duas, Arabic phrases, dhikr meanings, " +
                    "and everyday Islamic etiquette. Respond clearly in 2–5 short sentences unless the user asks for depth."
        }
        return content { text(guidance) }
    }

    companion object {
        private const val TAG = "NoorAiRepository"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
