package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DhikrLog
import com.example.data.model.DhikrProfile
import com.example.data.model.SavedVerse
import com.example.data.preferences.NoorPreferencesRepository
import com.example.data.preferences.NoorSettingsState
import com.example.data.repository.InspirationRepository
import com.example.data.repository.NoorFirestoreRepository
import com.example.domain.model.InspirationQuote
import com.example.domain.model.QuoteSourceType
import com.example.domain.model.RegionalPreset
import com.example.domain.solat.CalculationMethod
import com.example.domain.solat.SolatCalculator
import com.example.domain.solat.SolatSchedule
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class NoorViewModel(
    private val firestoreRepository: NoorFirestoreRepository,
    private val inspirationRepository: InspirationRepository,
    private val preferencesRepository: NoorPreferencesRepository,
    private val currentUserId: String,
    private val onSyncWidget: () -> Unit,
    private val onReschedulePrayers: (SolatSchedule, Boolean) -> Unit,
    private val onRescheduleDailyQuote: (Boolean) -> Unit
) : ViewModel() {

    val settings: StateFlow<NoorSettingsState> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NoorSettingsState()
        )

    val savedVerses: StateFlow<UiState<List<SavedVerse>>> = firestoreRepository.observeSavedVerses(currentUserId)
        .map<List<SavedVerse>, UiState<List<SavedVerse>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved verses", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load saved archive"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val dhikrLogs: StateFlow<List<DhikrLog>> = firestoreRepository.observeDhikrLogs(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing dhikr logs", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val dhikrProfile: StateFlow<DhikrProfile?> = firestoreRepository.observeDhikrProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing dhikr profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    private val _quoteFilter = MutableStateFlow<QuoteSourceType?>(null)
    val quoteFilter: StateFlow<QuoteSourceType?> = _quoteFilter.asStateFlow()

    private val _currentQuote = MutableStateFlow(
        inspirationRepository.getDailySeededQuote(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
    )
    val currentQuote: StateFlow<InspirationQuote> = _currentQuote.asStateFlow()

    private val _isLoadingQuote = MutableStateFlow(false)
    val isLoadingQuote: StateFlow<Boolean> = _isLoadingQuote.asStateFlow()

    private val _solatSchedule = MutableStateFlow(buildFallbackSchedule(NoorSettingsState()))
    val solatSchedule: StateFlow<SolatSchedule> = _solatSchedule.asStateFlow()

    private val _isLoadingSolat = MutableStateFlow(false)
    val isLoadingSolat: StateFlow<Boolean> = _isLoadingSolat.asStateFlow()

    private val _localSalawatToday = MutableStateFlow<Long?>(null)
    private val _localTasbihToday = MutableStateFlow<Long?>(null)
    private val _localCompletedPrayers = MutableStateFlow<List<String>?>(null)

    val todayDhikrLog: StateFlow<DhikrLog> = combine(
        dhikrLogs,
        settings,
        _localSalawatToday,
        _localTasbihToday,
        _localCompletedPrayers
    ) { logs, prefs, localSalawat, localTasbih, localPrayers ->
        val dateKey = SolatCalculator.todayDateKey(TimeZone.getTimeZone(prefs.timeZoneId))
        val remote = logs.firstOrNull { it.dateKey == dateKey }
        DhikrLog(
            id = dateKey,
            userId = currentUserId,
            dateKey = dateKey,
            salawatCount = localSalawat ?: remote?.salawatCount ?: 0L,
            tasbihCount = localTasbih ?: remote?.tasbihCount ?: 0L,
            dailyTarget = prefs.dailySalawatTarget,
            completedPrayers = localPrayers ?: remote?.completedPrayers ?: emptyList()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DhikrLog(
            id = SolatCalculator.todayDateKey(),
            userId = currentUserId,
            dateKey = SolatCalculator.todayDateKey()
        )
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _showSadaqahDialog = MutableStateFlow(false)
    val showSadaqahDialog: StateFlow<Boolean> = _showSadaqahDialog.asStateFlow()

    private var dhikrFlushJob: Job? = null

    init {
        viewModelScope.launch {
            val initialSettings = preferencesRepository.settingsFlow.first()
            refreshSolatSchedule(initialSettings)
            refreshDailyQuote(null, silent = true)
            onRescheduleDailyQuote(initialSettings.dailyQuoteNotificationsEnabled)
            if (!initialSettings.sadaqahPromptDismissed) {
                delay(1200L)
                _showSadaqahDialog.value = true
            }
        }
    }

    fun setQuoteFilter(type: QuoteSourceType?) {
        _quoteFilter.value = type
        refreshDailyQuote(type)
    }

    fun refreshDailyQuote(type: QuoteSourceType? = _quoteFilter.value, silent: Boolean = false) {
        viewModelScope.launch {
            _isLoadingQuote.value = true
            val quote = inspirationRepository.fetchLiveQuote(type)
            _currentQuote.value = quote
            _isLoadingQuote.value = false
            syncWidgetWithCurrentQuote()
            if (!silent) {
                _messages.tryEmit("Loaded from ${quote.sourceDomain}")
            }
        }
    }

    fun selectCuratedQuote(quote: InspirationQuote) {
        _currentQuote.value = quote
        viewModelScope.launch {
            syncWidgetWithCurrentQuote()
        }
    }

    fun toggleSaveCurrentQuote(reflectionNote: String = "") {
        val quote = _currentQuote.value
        val existingList = (savedVerses.value as? UiState.Success)?.data.orEmpty()
        val alreadySaved = existingList.any { it.id == quote.id }

        viewModelScope.launch {
            if (alreadySaved) {
                val res = firestoreRepository.deleteSavedVerse(quote.id)
                if (res.isSuccess) {
                    _messages.tryEmit("Removed from reflection archive")
                } else {
                    _messages.tryEmit("Could not remove verse: ${res.exceptionOrNull()?.localizedMessage ?: "Error"}")
                }
            } else {
                val res = firestoreRepository.saveVerse(
                    docIdHint = quote.id,
                    sourceType = quote.sourceType.raw,
                    reference = quote.reference,
                    arabicText = quote.arabicText,
                    translation = quote.translation,
                    sourceUrl = quote.sourceUrl,
                    tafsirUrl = quote.tafsirUrl,
                    reflectionNote = reflectionNote
                )
                if (res.isSuccess) {
                    _messages.tryEmit("Saved to your reflection archive")
                } else {
                    _messages.tryEmit("Failed to save: ${res.exceptionOrNull()?.localizedMessage ?: "Error"}")
                }
            }
        }
    }

    fun updateVerseReflection(verseId: String, note: String) {
        viewModelScope.launch {
            val res = firestoreRepository.updateReflectionNote(verseId, note)
            if (res.isSuccess) {
                _messages.tryEmit("Reflection note updated")
            } else {
                _messages.tryEmit("Could not update note")
            }
        }
    }

    fun deleteSavedVerse(verseId: String) {
        viewModelScope.launch {
            val res = firestoreRepository.deleteSavedVerse(verseId)
            if (res.isSuccess) {
                _messages.tryEmit("Removed from archive")
            }
        }
    }

    fun todayDateKey(): String {
        val tz = TimeZone.getTimeZone(settings.value.timeZoneId)
        return SolatCalculator.todayDateKey(tz)
    }

    fun currentTodayLog(): DhikrLog {
        val dateKey = todayDateKey()
        val remote = dhikrLogs.value.firstOrNull { it.dateKey == dateKey }
        return DhikrLog(
            id = dateKey,
            userId = currentUserId,
            dateKey = dateKey,
            salawatCount = _localSalawatToday.value ?: remote?.salawatCount ?: 0L,
            tasbihCount = _localTasbihToday.value ?: remote?.tasbihCount ?: 0L,
            dailyTarget = settings.value.dailySalawatTarget,
            completedPrayers = _localCompletedPrayers.value ?: remote?.completedPrayers ?: emptyList()
        )
    }

    fun incrementSalawat(delta: Long = 1L) {
        val log = currentTodayLog()
        val nextSalawat = (log.salawatCount + delta).coerceIn(0L, 1_000_000L)
        _localSalawatToday.value = nextSalawat
        _localTasbihToday.value = log.tasbihCount
        _localCompletedPrayers.value = log.completedPrayers
        scheduleDhikrSync(salawatDelta = delta)
    }

    fun incrementTasbih(delta: Long = 1L) {
        val log = currentTodayLog()
        val nextTasbih = (log.tasbihCount + delta).coerceIn(0L, 1_000_000L)
        _localSalawatToday.value = log.salawatCount
        _localTasbihToday.value = nextTasbih
        _localCompletedPrayers.value = log.completedPrayers
        scheduleDhikrSync(salawatDelta = 0L)
    }

    fun resetTodaySalawat() {
        val log = currentTodayLog()
        _localSalawatToday.value = 0L
        _localTasbihToday.value = log.tasbihCount
        _localCompletedPrayers.value = log.completedPrayers
        scheduleDhikrSync(salawatDelta = 0L)
    }

    fun togglePrayerCompleted(prayerName: String) {
        val log = currentTodayLog()
        val updated = log.completedPrayers.toMutableList().apply {
            if (contains(prayerName)) remove(prayerName) else add(prayerName)
        }
        _localSalawatToday.value = log.salawatCount
        _localTasbihToday.value = log.tasbihCount
        _localCompletedPrayers.value = updated
        scheduleDhikrSync(salawatDelta = 0L)
    }

    fun setDailySalawatTarget(target: Long) {
        viewModelScope.launch {
            preferencesRepository.setDailySalawatTarget(target)
            scheduleDhikrSync(salawatDelta = 0L)
        }
    }

    fun setActiveSalawatPhrase(phraseId: String) {
        viewModelScope.launch {
            preferencesRepository.setActiveSalawatPhrase(phraseId)
        }
    }

    private var pendingLifetimeDelta = 0L

    private fun scheduleDhikrSync(salawatDelta: Long) {
        pendingLifetimeDelta += salawatDelta
        dhikrFlushJob?.cancel()
        dhikrFlushJob = viewModelScope.launch {
            delay(650L)
            flushDhikrToFirestore()
        }
    }

    private suspend fun flushDhikrToFirestore() {
        val dateKey = todayDateKey()
        val salawat = _localSalawatToday.value ?: currentTodayLog().salawatCount
        val tasbih = _localTasbihToday.value ?: currentTodayLog().tasbihCount
        val prayers = _localCompletedPrayers.value ?: currentTodayLog().completedPrayers
        val target = settings.value.dailySalawatTarget

        firestoreRepository.saveDhikrLog(
            dateKey = dateKey,
            salawatCount = salawat,
            tasbihCount = tasbih,
            dailyTarget = target,
            completedPrayers = prayers
        )

        val delta = pendingLifetimeDelta
        pendingLifetimeDelta = 0L
        if (delta != 0L || dhikrProfile.value == null) {
            val currentProfile = dhikrProfile.value
            val prevLifetime = currentProfile?.lifetimeSalawat ?: 0L
            val newLifetime = (prevLifetime + delta).coerceAtLeast(salawat)
            val prevLastDate = currentProfile?.lastActiveDate.orEmpty()
            val prevStreak = currentProfile?.currentStreakDays ?: 0L
            val nextStreak = when {
                prevLastDate == dateKey -> prevStreak.coerceAtLeast(1L)
                prevLastDate.isBlank() -> 1L
                else -> prevStreak + 1L
            }
            val unlocked = InspirationRepository.dhikrMilestones
                .filter { newLifetime >= it.targetCount || salawat >= it.targetCount }
                .map { it.id }

            firestoreRepository.saveDhikrProfile(
                lifetimeSalawat = newLifetime,
                currentStreakDays = nextStreak,
                lastActiveDate = dateKey,
                unlockedMilestones = unlocked
            )
        }
    }

    fun applyRegionalPreset(preset: RegionalPreset) {
        viewModelScope.launch {
            val method = CalculationMethod.fromName(preset.methodName)
            preferencesRepository.updateLocation(
                label = preset.label,
                latitude = preset.latitude,
                longitude = preset.longitude,
                timeZoneId = preset.timeZoneId,
                method = method,
                jakimZone = preset.jakimZone
            )
            val updated = settings.value.copy(
                locationLabel = preset.label,
                latitude = preset.latitude,
                longitude = preset.longitude,
                timeZoneId = preset.timeZoneId,
                calculationMethod = method,
                jakimZone = preset.jakimZone ?: settings.value.jakimZone
            )
            refreshSolatSchedule(updated)
            _messages.tryEmit("Location updated to ${preset.label}")
        }
    }

    fun applyDetectedLocation(
        label: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: CalculationMethod,
        jakimZone: String?
    ) {
        viewModelScope.launch {
            preferencesRepository.updateLocation(
                label = label,
                latitude = latitude,
                longitude = longitude,
                timeZoneId = timeZoneId,
                method = method,
                jakimZone = jakimZone
            )
            val updated = settings.value.copy(
                locationLabel = label,
                latitude = latitude,
                longitude = longitude,
                timeZoneId = timeZoneId,
                calculationMethod = method,
                jakimZone = jakimZone ?: settings.value.jakimZone
            )
            refreshSolatSchedule(updated)
            _messages.tryEmit("Geolocation synced: $label")
        }
    }

    fun setCalculationMethod(method: CalculationMethod) {
        viewModelScope.launch {
            preferencesRepository.setCalculationMethod(method)
            val updated = settings.value.copy(calculationMethod = method)
            refreshSolatSchedule(updated)
        }
    }

    fun refreshSolatSchedule(state: NoorSettingsState = settings.value) {
        viewModelScope.launch {
            _isLoadingSolat.value = true
            _solatSchedule.value = buildFallbackSchedule(state)
            val live = inspirationRepository.fetchPrayerSchedule(
                latitude = state.latitude,
                longitude = state.longitude,
                timeZoneId = state.timeZoneId,
                locationLabel = state.locationLabel,
                method = state.calculationMethod,
                jakimZone = state.jakimZone
            )
            _solatSchedule.value = live
            _isLoadingSolat.value = false
            onReschedulePrayers(live, state.prayerRemindersEnabled)
            syncWidgetWithCurrentQuote()
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    fun toggleDailyQuoteNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDailyQuoteNotifications(enabled)
            onRescheduleDailyQuote(enabled)
        }
    }

    fun togglePrayerReminders(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPrayerReminders(enabled)
            onReschedulePrayers(_solatSchedule.value, enabled)
        }
    }

    fun toggleDhikrHaptics(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDhikrHapticsEnabled(enabled)
        }
    }

    fun openSadaqahDialog() {
        _showSadaqahDialog.value = true
    }

    fun dismissSadaqahDialog(blessingMessage: String? = null) {
        _showSadaqahDialog.value = false
        viewModelScope.launch {
            preferencesRepository.setSadaqahPromptDismissed(true)
            if (!blessingMessage.isNullOrBlank()) {
                _messages.tryEmit(blessingMessage)
            }
        }
    }

    fun syncWidgetManually() {
        viewModelScope.launch {
            syncWidgetWithCurrentQuote()
            _messages.tryEmit("Home screen widget synced")
        }
    }

    private suspend fun syncWidgetWithCurrentQuote() {
        val schedule = _solatSchedule.value
        val tz = TimeZone.getTimeZone(schedule.timeZoneId)
        val cal = Calendar.getInstance(tz)
        val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val (nextEntry, _) = schedule.nextPrayer(nowMins)
        val label = "${nextEntry.name} ${nextEntry.time.format12Hour()}"
        preferencesRepository.saveWidgetSnapshot(_currentQuote.value, label)
        onSyncWidget()
    }

    private fun buildFallbackSchedule(state: NoorSettingsState): SolatSchedule {
        val tz = TimeZone.getTimeZone(state.timeZoneId)
        return SolatCalculator.calculateDaySchedule(
            calendar = Calendar.getInstance(tz),
            latitude = state.latitude,
            longitude = state.longitude,
            timeZone = tz,
            locationLabel = state.locationLabel,
            method = state.calculationMethod
        )
    }

    private companion object {
        const val TAG = "NoorViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
