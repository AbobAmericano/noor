package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.DhikrLog
import com.example.data.model.DhikrProfile
import com.example.data.preferences.NoorSettingsState
import com.example.data.repository.InspirationRepository
import com.example.sensors.DhikrHapticHelper
import com.example.ui.theme.ArabicVerseTextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalawatScreen(
    todayLog: DhikrLog,
    profile: DhikrProfile?,
    settings: NoorSettingsState,
    onSelectPhrase: (String) -> Unit,
    onIncrementSalawat: (Long) -> Unit,
    onResetToday: () -> Unit,
    onSetTarget: (Long) -> Unit,
    onTogglePrayer: (String) -> Unit,
    onToggleHaptics: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val hapticHelper = remember(context) { DhikrHapticHelper(context) }
    val ringInteractionSource = remember { MutableInteractionSource() }
    val isRingPressed by ringInteractionSource.collectIsPressedAsState()
    val ringScale by animateFloatAsState(
        targetValue = if (isRingPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dhikr_ring_scale"
    )
    val activePhrase = InspirationRepository.salawatPhrases.firstOrNull {
        it.id == settings.activeSalawatPhraseId
    } ?: InspirationRepository.salawatPhrases.first()

    val target = settings.dailySalawatTarget.coerceAtLeast(1L)
    val count = todayLog.salawatCount
    val progress = (count.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val lifetime = (profile?.lifetimeSalawat ?: 0L).coerceAtLeast(count)
    val streakDays = profile?.currentStreakDays ?: if (count > 0) 1L else 0L

    val ringTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val ringProgressColor = MaterialTheme.colorScheme.primary
    val completedRingColor = MaterialTheme.colorScheme.secondary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Phrase Selector Strip
        item {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(InspirationRepository.salawatPhrases, key = { it.id }) { phrase ->
                    FilterChip(
                        selected = phrase.id == activePhrase.id,
                        onClick = { onSelectPhrase(phrase.id) },
                        label = { Text(phrase.title) }
                    )
                }
            }
        }

        // 2. Active Phrase Calligraphy & Counter Ring
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Digital Tasbih",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = settings.dhikrHapticsEnabled,
                            onClick = {
                                val nextEnabled = !settings.dhikrHapticsEnabled
                                onToggleHaptics(nextEnabled)
                                if (nextEnabled) {
                                    hapticHelper.performBeadTap(view, true)
                                }
                            },
                            label = {
                                Text(if (settings.dhikrHapticsEnabled) "Haptics On" else "Haptics Off")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Vibration,
                                    contentDescription = "Toggle Dhikr haptic feedback",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("dhikr_haptics_toggle")
                        )
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = activePhrase.arabic,
                            style = ArabicVerseTextStyle,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = activePhrase.transliteration,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = activePhrase.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tactile Ring Counter
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = ringInteractionSource,
                                indication = LocalIndication.current
                            ) {
                                val nextVal = count + 1L
                                hapticHelper.performCountFeedback(
                                    view = view,
                                    newCount = nextVal,
                                    dailyTarget = target,
                                    enabled = settings.dhikrHapticsEnabled
                                )
                                onIncrementSalawat(1L)
                            }
                            .testTag("salawat_tap_ring"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12.dp.toPx()
                            drawArc(
                                color = ringTrackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (count >= target) completedRingColor else ringProgressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "of $target target",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "TAP TO COUNT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (count > 0) {
                                    hapticHelper.performSoftUndo(view, settings.dhikrHapticsEnabled)
                                    onIncrementSalawat(-1L)
                                }
                            },
                            enabled = count > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Undo one", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("-1")
                        }

                        OutlinedButton(
                            onClick = {
                                val nextVal = count + 33L
                                if (nextVal >= target && count < target) {
                                    hapticHelper.performTargetGoalPulse(view, settings.dhikrHapticsEnabled)
                                } else {
                                    hapticHelper.performSubMilestonePulse(view, settings.dhikrHapticsEnabled)
                                }
                                onIncrementSalawat(33L)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("salawat_plus_33_button")
                        ) {
                            Text("+33")
                        }

                        OutlinedButton(
                            onClick = {
                                hapticHelper.performSoftUndo(view, settings.dhikrHapticsEnabled)
                                onResetToday()
                            },
                            enabled = count > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset today", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    // Daily Target Presets
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(33L, 99L, 100L, 300L, 1000L).forEach { preset ->
                            FilterChip(
                                selected = target == preset,
                                onClick = { onSetTarget(preset) },
                                label = { Text("Goal $preset") }
                            )
                        }
                    }
                }
            }
        }

        // 3. Daily 5-Prayer Milestone Tracker
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daily Prayer Milestones (${todayLog.completedPrayers.size}/5)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { prayer ->
                            val done = todayLog.completedPrayers.contains(prayer)
                            FilterChip(
                                selected = done,
                                onClick = { onTogglePrayer(prayer) },
                                label = { Text(prayer) },
                                leadingIcon = if (done) {
                                    {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // 4. Lifetime Stats & Spiritual Milestones
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Lifetime Salawat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(lifetime.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Active Streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$streakDays days", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    InspirationRepository.dhikrMilestones.forEach { milestone ->
                        val unlocked = lifetime >= milestone.targetCount || count >= milestone.targetCount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = milestone.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = milestone.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = if (unlocked) "Unlocked" else "${milestone.targetCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
