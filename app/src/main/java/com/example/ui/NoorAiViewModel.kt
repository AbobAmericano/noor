package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiChatMode
import com.example.data.ai.GroundedSourceLink
import com.example.data.ai.NoorAiRepository
import com.example.data.repository.IbadahPlace
import com.example.data.repository.IbadahPlaceCategory
import com.example.data.repository.MosqueFinderRepository
import com.google.firebase.ai.Chat
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.math.abs

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val modeLabel: String = "",
    val sources: List<GroundedSourceLink> = emptyList(),
    val isError: Boolean = false
)

data class AskAiUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val streamingText: String = "",
    val streamingSources: List<GroundedSourceLink> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val selectedMode: AiChatMode = AiChatMode.MOSQUE_MAPS,
    val draftInput: String = ""
)

data class MapsAiGuideState(
    val isLoading: Boolean = false,
    val text: String = "",
    val sources: List<GroundedSourceLink> = emptyList(),
    val error: String? = null
)

class NoorAiViewModel(
    private val aiRepository: NoorAiRepository = NoorAiRepository(),
    private val mosqueFinderRepository: MosqueFinderRepository = MosqueFinderRepository()
) : ViewModel() {

    private val _mosqueState = MutableStateFlow<UiState<List<IbadahPlace>>>(UiState.Loading)
    val mosqueState: StateFlow<UiState<List<IbadahPlace>>> = _mosqueState.asStateFlow()

    private val _selectedRadiusMeters = MutableStateFlow(5000)
    val selectedRadiusMeters: StateFlow<Int> = _selectedRadiusMeters.asStateFlow()

    private val _selectedCategory = MutableStateFlow(IbadahPlaceCategory.ALL)
    val selectedCategory: StateFlow<IbadahPlaceCategory> = _selectedCategory.asStateFlow()

    private val _mapsAiGuideState = MutableStateFlow(MapsAiGuideState())
    val mapsAiGuideState: StateFlow<MapsAiGuideState> = _mapsAiGuideState.asStateFlow()

    private val _chatUiState = MutableStateFlow(AskAiUiState())
    val chatUiState: StateFlow<AskAiUiState> = _chatUiState.asStateFlow()

    private var lastLoadedLat: Double? = null
    private var lastLoadedLon: Double? = null
    private var lastLoadedRadius: Int = -1
    private var mosqueJob: Job? = null
    private var mapsAiJob: Job? = null
    private var chatJob: Job? = null

    private var activeChat: Chat? = null
    private var activeChatMode: AiChatMode? = null
    private var activeChatLat: Double? = null
    private var activeChatLon: Double? = null

    fun ensureNearbyMosquesLoaded(latitude: Double, longitude: Double) {
        val radius = _selectedRadiusMeters.value
        val prevLat = lastLoadedLat
        val prevLon = lastLoadedLon
        if (prevLat != null &&
            prevLon != null &&
            abs(prevLat - latitude) < 0.005 &&
            abs(prevLon - longitude) < 0.005 &&
            lastLoadedRadius == radius &&
            _mosqueState.value is UiState.Success
        ) {
            return
        }
        refreshNearbyMosques(latitude, longitude, radius)
    }

    fun refreshNearbyMosques(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = _selectedRadiusMeters.value
    ) {
        _selectedRadiusMeters.value = radiusMeters
        lastLoadedLat = latitude
        lastLoadedLon = longitude
        lastLoadedRadius = radiusMeters

        mosqueJob?.cancel()
        mosqueJob = viewModelScope.launch {
            _mosqueState.value = UiState.Loading
            val result = mosqueFinderRepository.findNearbyIbadahPlaces(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters
            )
            result.fold(
                onSuccess = { places ->
                    _mosqueState.value = UiState.Success(places)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Nearby mosque lookup failed", error)
                    _mosqueState.value = UiState.Error(
                        error.localizedMessage ?: "Unable to fetch nearby mosques."
                    )
                }
            )
        }
    }

    fun selectPlaceCategory(category: IbadahPlaceCategory) {
        _selectedCategory.value = category
    }

    fun askMapsAiGuide(
        prompt: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ) {
        mapsAiJob?.cancel()
        mapsAiJob = viewModelScope.launch {
            _mapsAiGuideState.value = MapsAiGuideState(isLoading = true)
            val result = aiRepository.generateMapsMosqueGuide(
                prompt = prompt,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel
            ) { partialText, partialSources ->
                _mapsAiGuideState.value = MapsAiGuideState(
                    isLoading = true,
                    text = partialText,
                    sources = partialSources
                )
            }
            result.fold(
                onSuccess = { (finalText, sources) ->
                    _mapsAiGuideState.value = MapsAiGuideState(
                        isLoading = false,
                        text = finalText,
                        sources = sources
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _mapsAiGuideState.value = MapsAiGuideState(
                        isLoading = false,
                        error = error.localizedMessage ?: "Google Maps AI lookup failed."
                    )
                }
            )
        }
    }

    fun updateDraftInput(text: String) {
        _chatUiState.update { it.copy(draftInput = text) }
    }

    fun selectChatMode(mode: AiChatMode) {
        _chatUiState.update { it.copy(selectedMode = mode, errorMessage = null) }
    }

    fun resetChat() {
        chatJob?.cancel()
        activeChat = null
        activeChatMode = null
        activeChatLat = null
        activeChatLon = null
        _chatUiState.update {
            AskAiUiState(selectedMode = it.selectedMode)
        }
    }

    fun sendChatMessage(
        rawMessage: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        overrideMode: AiChatMode? = null
    ) {
        val trimmed = rawMessage.trim()
        if (trimmed.isBlank()) return

        val targetMode = overrideMode ?: _chatUiState.value.selectedMode
        if (overrideMode != null) {
            _chatUiState.update { it.copy(selectedMode = overrideMode) }
        }

        val userBubble = ChatMessageUi(
            isUser = true,
            text = trimmed
        )

        chatJob?.cancel()
        _chatUiState.update {
            it.copy(
                messages = it.messages + userBubble,
                streamingText = "",
                streamingSources = emptyList(),
                isGenerating = true,
                errorMessage = null,
                draftInput = ""
            )
        }

        chatJob = viewModelScope.launch {
            val accumulatedText = StringBuilder()
            val collectedSources = linkedMapOf<String, GroundedSourceLink>()
            try {
                val chat = resolveChatSession(
                    mode = targetMode,
                    latitude = latitude,
                    longitude = longitude,
                    locationLabel = locationLabel
                )
                withTimeout(NoorAiRepository.AI_TIMEOUT_MS) {
                    aiRepository.streamChatReply(chat, trimmed).collect { chunk ->
                        if (chunk.textDelta.isNotEmpty()) {
                            accumulatedText.append(chunk.textDelta)
                        }
                        chunk.sources.forEach { src ->
                            collectedSources[src.uri] = src
                        }
                        _chatUiState.update {
                            it.copy(
                                streamingText = accumulatedText.toString(),
                                streamingSources = collectedSources.values.toList()
                            )
                        }
                    }
                }

                val finalReply = accumulatedText.toString().trim()
                if (finalReply.isBlank()) {
                    _chatUiState.update {
                        it.copy(
                            isGenerating = false,
                            streamingText = "",
                            streamingSources = emptyList(),
                            errorMessage = "Gemini returned an empty response."
                        )
                    }
                } else {
                    val assistantBubble = ChatMessageUi(
                        isUser = false,
                        text = finalReply,
                        modeLabel = targetMode.label,
                        sources = collectedSources.values.toList()
                    )
                    _chatUiState.update {
                        it.copy(
                            messages = it.messages + assistantBubble,
                            isGenerating = false,
                            streamingText = "",
                            streamingSources = emptyList()
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat reply timed out", e)
                _chatUiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        streamingSources = emptyList(),
                        errorMessage = "Response timed out after 60 seconds. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat reply failed", e)
                _chatUiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        streamingSources = emptyList(),
                        errorMessage = e.localizedMessage ?: "Unable to reach Firebase AI Logic."
                    )
                }
            }
        }
    }

    private fun resolveChatSession(
        mode: AiChatMode,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): Chat {
        val current = activeChat
        val locationShifted = activeChatLat == null ||
            activeChatLon == null ||
            abs((activeChatLat ?: 0.0) - latitude) > 0.01 ||
            abs((activeChatLon ?: 0.0) - longitude) > 0.01

        if (current != null && activeChatMode == mode && (!mode.usesMapsGrounding || !locationShifted)) {
            return current
        }

        val existingHistory = current?.history ?: _chatUiState.value.messages
            .filter { !it.isError }
            .dropLast(1) // exclude the just-added user turn that sendMessageStream will send
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") { text(msg.text) }
            }

        val newChat = aiRepository.startChatSession(
            mode = mode,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel,
            history = existingHistory
        )
        activeChat = newChat
        activeChatMode = mode
        activeChatLat = latitude
        activeChatLon = longitude
        return newChat
    }

    companion object {
        private const val TAG = "NoorAiViewModel"
    }
}
