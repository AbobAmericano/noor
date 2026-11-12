package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.preferences.NoorSettingsState
import com.example.domain.model.InspirationQuote
import com.example.notifications.NoorNotificationHelper
import com.example.ui.theme.ArabicVerseTextStyle
import com.example.ui.theme.QuoteEditorialTextStyle

fun shareQuoteText(
    context: Context,
    reference: String,
    arabicText: String,
    translation: String,
    sourceUrl: String
) {
    val body = buildString {
        if (arabicText.isNotBlank()) {
            appendLine(arabicText)
            appendLine()
        }
        appendLine("\"$translation\"")
        appendLine("— $reference")
        if (sourceUrl.isNotBlank()) {
            appendLine(sourceUrl)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, reference)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share Reflection"))
}

fun openExternalUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}

@Composable
fun InspirationQuoteCard(
    quote: InspirationQuote,
    isSaved: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onOpenTafsir: () -> Unit,
    onAskAi: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${quote.sourceType.displayLabel.uppercase()} • ${quote.sourceDomain.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_quote_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "New daily inspiration",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (quote.arabicText.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = quote.arabicText,
                        style = ArabicVerseTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "“${quote.translation}”",
                style = QuoteEditorialTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = quote.reference,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = quote.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleSave,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.testTag("save_quote_button")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isSaved) "Saved" else "Save verse",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSaved) "Saved" else "Save")
                }

                TextButton(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.testTag("share_quote_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share verse",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                TextButton(
                    onClick = onOpenTafsir,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.testTag("open_tafsir_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "Read Tafsir or Hadith source",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (quote.sourceType.raw == "QURAN") "Tafsir" else "Source")
                }

                if (onAskAi != null) {
                    TextButton(
                        onClick = onAskAi,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.testTag("ask_ai_quote_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Ask AI about this quote",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ask AI")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoorSettingsSheet(
    userEmail: String?,
    settings: NoorSettingsState,
    currentQuote: InspirationQuote,
    onDismiss: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleDailyQuoteNotif: (Boolean) -> Unit,
    onTogglePrayerReminders: (Boolean) -> Unit,
    onToggleDhikrHaptics: (Boolean) -> Unit,
    onSyncWidget: () -> Unit,
    onOpenSadaqah: () -> Unit,
    onSignOut: () -> Unit,
    onNotifyFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onNotifyFeedback("Notifications enabled")
        } else {
            onNotifyFeedback("Notification permission denied")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Preferences & Alerts",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = "Synced with Google: $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Night Reading Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Warm obsidian surface for low-eye-strain reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.isDarkMode,
                    onCheckedChange = onToggleDarkMode,
                    modifier = Modifier.testTag("dark_mode_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dhikr Tactile Haptics", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Crisp vibration feedback on every Salawat bead & 33-count milestone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.dhikrHapticsEnabled,
                    onCheckedChange = onToggleDhikrHaptics,
                    modifier = Modifier.testTag("dhikr_haptics_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Inspiration Alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Morning Quran & Hadith push reminder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.dailyQuoteNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NoorNotificationHelper.hasNotificationPermission(context)
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onToggleDailyQuoteNotif(enabled)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Solat Prayer Reminders", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Push alerts for Fajr, Dhuhr, Asr, Maghrib & Isha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.prayerRemindersEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NoorNotificationHelper.hasNotificationPermission(context)
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onTogglePrayerReminders(enabled)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !NoorNotificationHelper.hasNotificationPermission(context)
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            NoorNotificationHelper.showDailyInspirationNotification(context, currentQuote)
                            onNotifyFeedback("Test notification dispatched")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Alert")
                }

                OutlinedButton(
                    onClick = {
                        onSyncWidget()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Widget")
                }
            }

            Text(
                text = "Tip: Long-press your Android home screen → Widgets → Noor Daily Verse to add the lightweight home screen widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onOpenSadaqah,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_sadaqah_button")
            ) {
                Icon(Icons.Filled.VolunteerActivism, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Give Sadaqah (2 MYR)")
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}
