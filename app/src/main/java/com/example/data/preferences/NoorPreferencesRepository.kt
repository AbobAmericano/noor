package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.InspirationQuote
import com.example.domain.solat.CalculationMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.TimeZone

private val Context.noorDataStore: DataStore<Preferences> by preferencesDataStore(name = "noor_prefs")

data class NoorSettingsState(
    val isDarkMode: Boolean = true,
    val dailyQuoteNotificationsEnabled: Boolean = true,
    val prayerRemindersEnabled: Boolean = true,
    val calculationMethod: CalculationMethod = CalculationMethod.JAKIM,
    val jakimZone: String = "WLY01",
    val locationLabel: String = "Kuala Lumpur, Malaysia",
    val latitude: Double = 3.1390,
    val longitude: Double = 101.6869,
    val timeZoneId: String = TimeZone.getDefault().id,
    val dailySalawatTarget: Long = 100L,
    val activeSalawatPhraseId: String = "salawat_short",
    val dhikrHapticsEnabled: Boolean = true,
    val sadaqahPromptDismissed: Boolean = false
)

data class WidgetSnapshot(
    val reference: String,
    val arabicText: String,
    val translation: String,
    val sourceType: String,
    val nextPrayerLabel: String
)

class NoorPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val DAILY_QUOTE_NOTIF = booleanPreferencesKey("daily_quote_notif")
        val PRAYER_REMINDER_NOTIF = booleanPreferencesKey("prayer_reminder_notif")
        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val JAKIM_ZONE = stringPreferencesKey("jakim_zone")
        val LOCATION_LABEL = stringPreferencesKey("location_label")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val TIMEZONE_ID = stringPreferencesKey("timezone_id")
        val DAILY_SALAWAT_TARGET = longPreferencesKey("daily_salawat_target")
        val ACTIVE_SALAWAT_PHRASE = stringPreferencesKey("active_salawat_phrase")
        val DHIKR_HAPTICS_ENABLED = booleanPreferencesKey("dhikr_haptics_enabled")
        val SADAQAH_PROMPT_DISMISSED = booleanPreferencesKey("sadaqah_prompt_dismissed")

        val WIDGET_REF = stringPreferencesKey("widget_ref")
        val WIDGET_ARABIC = stringPreferencesKey("widget_arabic")
        val WIDGET_TRANSLATION = stringPreferencesKey("widget_translation")
        val WIDGET_SOURCE_TYPE = stringPreferencesKey("widget_source_type")
        val WIDGET_NEXT_PRAYER = stringPreferencesKey("widget_next_prayer")
    }

    val settingsFlow: Flow<NoorSettingsState> = context.noorDataStore.data.map { prefs ->
        NoorSettingsState(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            dailyQuoteNotificationsEnabled = prefs[Keys.DAILY_QUOTE_NOTIF] ?: true,
            prayerRemindersEnabled = prefs[Keys.PRAYER_REMINDER_NOTIF] ?: true,
            calculationMethod = CalculationMethod.fromName(prefs[Keys.CALCULATION_METHOD] ?: CalculationMethod.JAKIM.name),
            jakimZone = prefs[Keys.JAKIM_ZONE] ?: "WLY01",
            locationLabel = prefs[Keys.LOCATION_LABEL] ?: "Kuala Lumpur, Malaysia",
            latitude = prefs[Keys.LATITUDE] ?: 3.1390,
            longitude = prefs[Keys.LONGITUDE] ?: 101.6869,
            timeZoneId = prefs[Keys.TIMEZONE_ID] ?: TimeZone.getDefault().id,
            dailySalawatTarget = prefs[Keys.DAILY_SALAWAT_TARGET] ?: 100L,
            activeSalawatPhraseId = prefs[Keys.ACTIVE_SALAWAT_PHRASE] ?: "salawat_short",
            dhikrHapticsEnabled = prefs[Keys.DHIKR_HAPTICS_ENABLED] ?: true,
            sadaqahPromptDismissed = prefs[Keys.SADAQAH_PROMPT_DISMISSED] ?: false
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun setDailyQuoteNotifications(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.DAILY_QUOTE_NOTIF] = enabled }
    }

    suspend fun setPrayerReminders(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.PRAYER_REMINDER_NOTIF] = enabled }
    }

    suspend fun setDhikrHapticsEnabled(enabled: Boolean) {
        context.noorDataStore.edit { it[Keys.DHIKR_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setSadaqahPromptDismissed(dismissed: Boolean) {
        context.noorDataStore.edit { it[Keys.SADAQAH_PROMPT_DISMISSED] = dismissed }
    }

    suspend fun setCalculationMethod(method: CalculationMethod) {
        context.noorDataStore.edit { it[Keys.CALCULATION_METHOD] = method.name }
    }

    suspend fun updateLocation(
        label: String,
        latitude: Double,
        longitude: Double,
        timeZoneId: String,
        method: CalculationMethod? = null,
        jakimZone: String? = null
    ) {
        context.noorDataStore.edit { prefs ->
            prefs[Keys.LOCATION_LABEL] = label
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.TIMEZONE_ID] = timeZoneId
            if (method != null) prefs[Keys.CALCULATION_METHOD] = method.name
            if (jakimZone != null) prefs[Keys.JAKIM_ZONE] = jakimZone
        }
    }

    suspend fun setDailySalawatTarget(target: Long) {
        context.noorDataStore.edit { it[Keys.DAILY_SALAWAT_TARGET] = target.coerceIn(1L, 100_000L) }
    }

    suspend fun setActiveSalawatPhrase(phraseId: String) {
        context.noorDataStore.edit { it[Keys.ACTIVE_SALAWAT_PHRASE] = phraseId }
    }

    suspend fun saveWidgetSnapshot(quote: InspirationQuote, nextPrayerLabel: String) {
        context.noorDataStore.edit { prefs ->
            prefs[Keys.WIDGET_REF] = quote.reference
            prefs[Keys.WIDGET_ARABIC] = quote.arabicText
            prefs[Keys.WIDGET_TRANSLATION] = quote.translation
            prefs[Keys.WIDGET_SOURCE_TYPE] = quote.sourceType.displayLabel
            prefs[Keys.WIDGET_NEXT_PRAYER] = nextPrayerLabel
        }
    }

    suspend fun readWidgetSnapshot(): WidgetSnapshot? {
        val prefs = context.noorDataStore.data.first()
        val ref = prefs[Keys.WIDGET_REF] ?: return null
        val trans = prefs[Keys.WIDGET_TRANSLATION] ?: return null
        return WidgetSnapshot(
            reference = ref,
            arabicText = prefs[Keys.WIDGET_ARABIC].orEmpty(),
            translation = trans,
            sourceType = prefs[Keys.WIDGET_SOURCE_TYPE] ?: "Al-Quran",
            nextPrayerLabel = prefs[Keys.WIDGET_NEXT_PRAYER].orEmpty()
        )
    }

    suspend fun readSettingsOnce(): NoorSettingsState = settingsFlow.first()
}
