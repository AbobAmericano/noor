package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiChatMode
import com.example.data.model.DhikrLog
import com.example.data.repository.InspirationRepository
import com.example.domain.model.RegionalPreset
import com.example.domain.solat.CalculationMethod
import com.example.domain.solat.SolatCalculator
import com.example.domain.solat.SolatSchedule
import com.example.sensors.CompassReading
import com.example.sensors.GeolocationHelper
import com.example.sensors.QiblaSensorManager
import com.example.ui.NoorAiViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolatQiblaScreen(
    solatSchedule: SolatSchedule,
    isLoadingSolat: Boolean,
    todayDhikrLog: DhikrLog,
    aiViewModel: NoorAiViewModel,
    initialSubTab: Int = 0,
    onTogglePrayerCompleted: (String) -> Unit,
    onSelectPreset: (RegionalPreset) -> Unit,
    onDetectedLocation: (String, Double, Double, String, CalculationMethod, String?) -> Unit,
    onSelectMethod: (CalculationMethod) -> Unit,
    onAskAiWithPrompt: (String, AiChatMode) -> Unit,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geoHelper = remember(context) { GeolocationHelper(context) }

    var selectedSubTab by remember { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    LaunchedEffect(initialSubTab) {
        if (initialSubTab in 0..2) {
            selectedSubTab = initialSubTab
        }
    }
    LaunchedEffect(selectedSubTab, solatSchedule.latitude, solatSchedule.longitude) {
        if (selectedSubTab == 2) {
            aiViewModel.ensureNearbyMosquesLoaded(solatSchedule.latitude, solatSchedule.longitude)
        }
    }
    var showRegionDialog by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val resolved = geoHelper.fetchCurrentLocation()
                if (resolved != null) {
                    onDetectedLocation(
                        resolved.label,
                        resolved.latitude,
                        resolved.longitude,
                        resolved.timeZoneId,
                        resolved.suggestedMethod,
                        resolved.jakimZone
                    )
                } else {
                    onFeedback("Unable to fetch coordinates right now. Choose a region preset.")
                }
            }
        } else {
            onFeedback("Location permission denied. You can choose any region manually.")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SegmentedButton(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                modifier = Modifier.testTag("solat_subtab_times")
            ) {
                Text("Times")
            }
            SegmentedButton(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                modifier = Modifier.testTag("solat_subtab_qibla")
            ) {
                Text("Qibla")
            }
            SegmentedButton(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                modifier = Modifier.testTag("solat_subtab_mosques")
            ) {
                Text("Mosques")
            }
        }

        if (selectedSubTab == 0) {
            val tz = TimeZone.getTimeZone(solatSchedule.timeZoneId)
            val cal = Calendar.getInstance(tz)
            val nowMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val (nextPrayer, _) = solatSchedule.nextPrayer(nowMins)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = solatSchedule.locationLabel,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${solatSchedule.hijriLabel} • ${solatSchedule.timeZoneId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isLoadingSolat) "Syncing official timetable…" else solatSchedule.sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        if (geoHelper.hasCoarseLocationPermission()) {
                                            scope.launch {
                                                val resolved = geoHelper.fetchCurrentLocation()
                                                if (resolved != null) {
                                                    onDetectedLocation(
                                                        resolved.label,
                                                        resolved.latitude,
                                                        resolved.longitude,
                                                        resolved.timeZoneId,
                                                        resolved.suggestedMethod,
                                                        resolved.jakimZone
                                                    )
                                                } else {
                                                    onFeedback("GPS unavailable; using regional preset.")
                                                }
                                            }
                                        } else {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("detect_gps_button")
                                ) {
                                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GPS")
                                }

                                OutlinedButton(
                                    onClick = { showRegionDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("select_region_button")
                                ) {
                                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Region")
                                }

                                OutlinedButton(
                                    onClick = { showMethodDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("select_method_button")
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(solatSchedule.method.shortLabel)
                                }
                            }
                        }
                    }
                }

                items(solatSchedule.entries, key = { it.name }) { entry ->
                    val isNext = entry.name == nextPrayer.name
                    val isDone = todayDhikrLog.completedPrayers.contains(entry.name)
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = entry.isObligatory) {
                                onTogglePrayerCompleted(entry.name)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isNext) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (entry.isObligatory) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = if (isDone) "${entry.name} completed" else "Mark ${entry.name} completed",
                                        tint = if (isDone) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(22.dp))
                                }

                                Column {
                                    Text(
                                        text = "${entry.name}  •  ${entry.arabicName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isNext) {
                                        Text(
                                            text = "Upcoming Prayer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = entry.time.format12Hour(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else if (selectedSubTab == 1) {
            QiblaCompassPane(
                latitude = solatSchedule.latitude,
                longitude = solatSchedule.longitude,
                locationLabel = solatSchedule.locationLabel
            )
        } else {
            val mosqueState by aiViewModel.mosqueState.collectAsStateWithLifecycle()
            val selectedRadius by aiViewModel.selectedRadiusMeters.collectAsStateWithLifecycle()
            val selectedCategory by aiViewModel.selectedCategory.collectAsStateWithLifecycle()
            val mapsAiGuideState by aiViewModel.mapsAiGuideState.collectAsStateWithLifecycle()

            MosqueFinderPanel(
                solatSchedule = solatSchedule,
                mosqueState = mosqueState,
                selectedRadiusMeters = selectedRadius,
                selectedCategory = selectedCategory,
                mapsAiGuideState = mapsAiGuideState,
                onRequestGpsSync = {
                    if (geoHelper.hasCoarseLocationPermission()) {
                        scope.launch {
                            val resolved = geoHelper.fetchCurrentLocation()
                            if (resolved != null) {
                                onDetectedLocation(
                                    resolved.label,
                                    resolved.latitude,
                                    resolved.longitude,
                                    resolved.timeZoneId,
                                    resolved.suggestedMethod,
                                    resolved.jakimZone
                                )
                                aiViewModel.refreshNearbyMosques(
                                    resolved.latitude,
                                    resolved.longitude,
                                    selectedRadius
                                )
                            } else {
                                onFeedback("GPS unavailable; showing places for ${solatSchedule.locationLabel}.")
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                onRefreshPlaces = { radius ->
                    aiViewModel.refreshNearbyMosques(
                        latitude = solatSchedule.latitude,
                        longitude = solatSchedule.longitude,
                        radiusMeters = radius
                    )
                },
                onSelectCategory = aiViewModel::selectPlaceCategory,
                onAskMapsAi = { prompt ->
                    aiViewModel.askMapsAiGuide(
                        prompt = prompt,
                        latitude = solatSchedule.latitude,
                        longitude = solatSchedule.longitude,
                        locationLabel = solatSchedule.locationLabel
                    )
                },
                onAskAiInChat = { prompt ->
                    onAskAiWithPrompt(prompt, AiChatMode.MOSQUE_MAPS)
                }
            )
        }
    }

    if (showRegionDialog) {
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text("Select Solat Region") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InspirationRepository.regionalPresets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectPreset(preset)
                                    showRegionDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(preset.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    preset.timeZoneId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRegionDialog = false }) { Text("Close") }
            }
        )
    }

    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Calculation Method") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalculationMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectMethod(method)
                                    showMethodDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = method.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (method == solatSchedule.method) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMethodDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun QiblaCompassPane(
    latitude: Double,
    longitude: Double,
    locationLabel: String
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sensorManager = remember(context) { QiblaSensorManager(context) }
    val reading by sensorManager.observeAzimuth(latitude, longitude)
        .collectAsStateWithLifecycle(initialValue = CompassReading())

    val qiblaBearing = remember(latitude, longitude) {
        SolatCalculator.qiblaBearingDegrees(latitude, longitude).toFloat()
    }
    val distanceKm = remember(latitude, longitude) {
        SolatCalculator.distanceToKaabaKm(latitude, longitude)
    }

    val diff = (((qiblaBearing - reading.azimuthDegrees) + 540f) % 360f) - 180f
    val isAligned = reading.hasCompassSensor && abs(diff) <= 3.5f

    LaunchedEffect(isAligned) {
        if (isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val ringColor = MaterialTheme.colorScheme.outline
    val goldColor = MaterialTheme.colorScheme.primary
    val emeraldColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = surfaceColor,
                border = BorderStroke(
                    1.5.dp,
                    if (isAligned) emeraldColor else ringColor.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = null,
                            tint = if (isAligned) emeraldColor else goldColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isAligned) "Facing Qibla" else "Align Arrow With Top Index",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isAligned) emeraldColor else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier.size(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f - 12.dp.toPx()

                            // Outer compass ring
                            drawCircle(
                                color = if (isAligned) emeraldColor.copy(alpha = 0.14f) else Color.Transparent,
                                radius = radius,
                                center = center
                            )
                            drawCircle(
                                color = if (isAligned) emeraldColor else ringColor,
                                radius = radius,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = ringColor.copy(alpha = 0.45f),
                                radius = radius * 0.72f,
                                center = center,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Fixed top reference notch
                            drawLine(
                                color = if (isAligned) emeraldColor else goldColor,
                                start = Offset(center.x, center.y - radius - 8.dp.toPx()),
                                end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Rotating compass dial ticks
                            rotate(degrees = -reading.azimuthDegrees, pivot = center) {
                                for (deg in 0 until 360 step 15) {
                                    val isMajor = deg % 90 == 0
                                    val tickStart = radius - if (isMajor) 14.dp.toPx() else 7.dp.toPx()
                                    rotate(degrees = deg.toFloat(), pivot = center) {
                                        drawLine(
                                            color = if (deg == 0) goldColor else ringColor,
                                            start = Offset(center.x, center.y - radius),
                                            end = Offset(center.x, center.y - tickStart),
                                            strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            // Qibla needle
                            val needleRotation = if (reading.hasCompassSensor) {
                                qiblaBearing - reading.azimuthDegrees
                            } else {
                                qiblaBearing
                            }
                            rotate(degrees = needleRotation, pivot = center) {
                                val pointerPath = Path().apply {
                                    moveTo(center.x, center.y - radius * 0.78f)
                                    lineTo(center.x - 11.dp.toPx(), center.y + 12.dp.toPx())
                                    lineTo(center.x, center.y + 2.dp.toPx())
                                    lineTo(center.x + 11.dp.toPx(), center.y + 12.dp.toPx())
                                    close()
                                }
                                drawPath(
                                    path = pointerPath,
                                    color = if (isAligned) emeraldColor else goldColor
                                )
                            }

                            drawCircle(
                                color = if (isAligned) emeraldColor else goldColor,
                                radius = 6.dp.toPx(),
                                center = center
                            )
                        }
                    }

                    Text(
                        text = String.format(Locale.US, "Qibla Bearing: %.1f° from True North", qiblaBearing),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "$locationLabel • $distanceKm km to Makkah\nSensor: ${reading.accuracyLabel} • Heading ${reading.azimuthDegrees.toInt()}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!reading.hasCompassSensor) {
                        Text(
                            text = "This device or emulator has no magnetometer hardware; showing true bearing from North.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
