package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DhikrLog
import com.example.data.model.SavedVerse
import com.example.data.preferences.NoorSettingsState
import com.example.data.repository.InspirationRepository
import com.example.domain.model.InspirationQuote
import com.example.domain.model.QuoteSourceType
import com.example.domain.solat.SolatSchedule
import com.example.sensors.DhikrHapticHelper
import com.example.ui.UiState
import com.example.ui.components.InspirationQuoteCard
import com.example.ui.components.openExternalUrl
import com.example.ui.components.shareQuoteText
import java.util.Calendar
import java.util.TimeZone

@Composable
fun TodayScreen(
    currentQuote: InspirationQuote,
    isLoadingQuote: Boolean,
    quoteFilter: QuoteSourceType?,
    savedVersesState: UiState<List<SavedVerse>>,
    solatSchedule: SolatSchedule,
    todayDhikrLog: DhikrLog,
    settings: NoorSettingsState,
    onSelectFilter: (QuoteSourceType?) -> Unit,
    onRefreshQuote: () -> Unit,
    onSelectCuratedQuote: (InspirationQuote) -> Unit,
    onToggleSaveQuote: () -> Unit,
    onQuickIncrementSalawat: () -> Unit,
    onAskAiAboutQuote: (InspirationQuote) -> Unit,
    onNavigateToSolat: () -> Unit,
    onNavigateToMosques: () -> Unit,
    onNavigateToSalawat: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticHelper = remember(context) { DhikrHapticHelper(context) }
    val savedList = (savedVersesState as? UiState.Success)?.data.orEmpty()
    val isCurrentSaved = savedList.any { it.id == currentQuote.id }

    val tz = TimeZone.getTimeZone(solatSchedule.timeZoneId)
    val nowCal = Calendar.getInstance(tz)
    val nowMins = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val (nextPrayer, minsRemaining) = solatSchedule.nextPrayer(nowMins)
    val countdownLabel = if (minsRemaining >= 60) {
        "in ${minsRemaining / 60}h ${minsRemaining % 60}m"
    } else {
        "in ${minsRemaining}m"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Minimalist Atmospheric Header Card
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onNavigateToSolat),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (settings.isDarkMode) {
                        Image(
                            painter = painterResource(id = R.drawable.img_noor_header_dark),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.38f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = solatSchedule.hijriLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = solatSchedule.gregorianLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "${nextPrayer.name} ${nextPrayer.time.format12Hour()} ($countdownLabel)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${solatSchedule.locationLabel} • ${solatSchedule.timeZoneId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            AssistChip(
                                onClick = onNavigateToMosques,
                                label = { Text("Mosques") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Mosque,
                                        contentDescription = "Find nearby mosques and prayer places",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("today_nearby_mosques_button")
                            )
                        }
                    }
                }
            }
        }

        // 2. Source Filter Chips (All / Al-Quran / Hadith)
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = quoteFilter == null,
                    onClick = { onSelectFilter(null) },
                    label = { Text("Daily Mix") },
                    modifier = Modifier.testTag("filter_all_quotes")
                )
                FilterChip(
                    selected = quoteFilter == QuoteSourceType.QURAN,
                    onClick = { onSelectFilter(QuoteSourceType.QURAN) },
                    label = { Text("Quran.com") },
                    modifier = Modifier.testTag("filter_quran_quotes")
                )
                FilterChip(
                    selected = quoteFilter == QuoteSourceType.HADITH,
                    onClick = { onSelectFilter(QuoteSourceType.HADITH) },
                    label = { Text("Sunnah.com") },
                    modifier = Modifier.testTag("filter_hadith_quotes")
                )
            }
        }

        // 3. Featured Quote Card
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                InspirationQuoteCard(
                    quote = currentQuote,
                    isSaved = isCurrentSaved,
                    isLoading = isLoadingQuote,
                    onRefresh = onRefreshQuote,
                    onToggleSave = onToggleSaveQuote,
                    onShare = {
                        shareQuoteText(
                            context = context,
                            reference = currentQuote.reference,
                            arabicText = currentQuote.arabicText,
                            translation = currentQuote.translation,
                            sourceUrl = currentQuote.sourceUrl
                        )
                    },
                    onOpenTafsir = {
                        openExternalUrl(context, currentQuote.tafsirUrl)
                    },
                    onAskAi = {
                        onAskAiAboutQuote(currentQuote)
                    }
                )
            }
        }

        // 4. Quick Salawat & Daily Prayer Milestone Strip
        item {
            val target = settings.dailySalawatTarget.coerceAtLeast(1L)
            val progress = (todayDhikrLog.salawatCount.toFloat() / target.toFloat()).coerceIn(0f, 1f)
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onNavigateToSalawat),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today's Salawat & Solat",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${todayDhikrLog.salawatCount} / $target Salawat • ${todayDhikrLog.completedPrayers.size}/5 Prayers",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                hapticHelper.performCountFeedback(
                                    view = view,
                                    newCount = todayDhikrLog.salawatCount + 1L,
                                    dailyTarget = target,
                                    enabled = settings.dhikrHapticsEnabled
                                )
                                onQuickIncrementSalawat()
                            },
                            modifier = Modifier.testTag("quick_salawat_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Count Salawat", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salawat")
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // 5. Curated Classics Carousel
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Contemplative Selections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(InspirationRepository.curatedQuotes, key = { it.id }) { itemQuote ->
                        val selected = itemQuote.id == currentQuote.id
                        Surface(
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectCuratedQuote(itemQuote) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = itemQuote.sourceType.displayLabel.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = itemQuote.translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = itemQuote.reference,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
