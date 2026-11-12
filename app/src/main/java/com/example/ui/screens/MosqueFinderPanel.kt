package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.GroundedSourceLink
import com.example.data.repository.IbadahPlace
import com.example.data.repository.IbadahPlaceCategory
import com.example.domain.solat.SolatSchedule
import com.example.ui.MapsAiGuideState
import com.example.ui.UiState
import com.example.ui.components.openExternalUrl

fun openGoogleMapsLocation(context: Context, lat: Double, lon: Double, queryLabel: String) {
    val encoded = Uri.encode(queryLabel)
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($encoded)"))
    val launched = runCatching {
        context.startActivity(geoIntent)
        true
    }.getOrDefault(false)
    if (!launched) {
        openExternalUrl(
            context,
            "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
        )
    }
}

fun openGoogleMapsSearchNearby(context: Context, lat: Double, lon: Double) {
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=mosque+surau+prayer+room"))
    val launched = runCatching {
        context.startActivity(geoIntent)
        true
    }.getOrDefault(false)
    if (!launched) {
        openExternalUrl(
            context,
            "https://www.google.com/maps/search/mosque+surau+prayer+room/@$lat,$lon,14z"
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MosqueFinderPanel(
    solatSchedule: SolatSchedule,
    mosqueState: UiState<List<IbadahPlace>>,
    selectedRadiusMeters: Int,
    selectedCategory: IbadahPlaceCategory,
    mapsAiGuideState: MapsAiGuideState,
    onRequestGpsSync: () -> Unit,
    onRefreshPlaces: (Int) -> Unit,
    onSelectCategory: (IbadahPlaceCategory) -> Unit,
    onAskMapsAi: (String) -> Unit,
    onAskAiInChat: (String) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Search Controls Header
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nearby Mosques & Ibadah Places",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Centered at ${solatSchedule.locationLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Mosque,
                            contentDescription = "Mosque directory",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3000 to "3 km", 5000 to "5 km", 10000 to "10 km").forEach { (meters, label) ->
                            FilterChip(
                                selected = selectedRadiusMeters == meters,
                                onClick = { onRefreshPlaces(meters) },
                                label = { Text(label) }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IbadahPlaceCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onSelectCategory(category) },
                                label = { Text(category.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRequestGpsSync,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mosque_gps_button")
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use GPS")
                        }

                        OutlinedButton(
                            onClick = { onRefreshPlaces(selectedRadiusMeters) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mosque_refresh_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh")
                        }

                        OutlinedButton(
                            onClick = {
                                openGoogleMapsSearchNearby(
                                    context = context,
                                    lat = solatSchedule.latitude,
                                    lon = solatSchedule.longitude
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_google_maps_nearby_button")
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Maps")
                        }
                    }
                }
            }
        }

        // 2. Google Maps Grounded AI Summary Card
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Google Maps AI Guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI Mosque & Prayer Room Guide (Google Maps)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Get live Google Maps grounded recommendations for mosques, suraus, wudu spaces, and Friday prayer near your location — no API key required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onAskMapsAi(
                                    "List the closest mosques, suraus, or prayer rooms near ${solatSchedule.locationLabel} " +
                                        "with brief details about accessibility, wudu, and parking."
                                )
                            },
                            enabled = !mapsAiGuideState.isLoading,
                            modifier = Modifier.testTag("ask_maps_ai_nearby_button")
                        ) {
                            Text(if (mapsAiGuideState.isLoading) "Searching Maps…" else "Ask Maps AI Nearby")
                        }

                        OutlinedButton(
                            onClick = {
                                onAskAiInChat(
                                    "Help me find a nearby mosque or surau for prayer around ${solatSchedule.locationLabel} " +
                                        "with wudu and women's prayer space if available."
                                )
                            },
                            modifier = Modifier.testTag("open_mosque_chat_button")
                        ) {
                            Icon(Icons.Filled.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open AI Chat")
                        }
                    }

                    if (mapsAiGuideState.isLoading && mapsAiGuideState.text.isBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Querying Gemini with Google Maps grounding…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (mapsAiGuideState.error != null) {
                        Text(
                            text = mapsAiGuideState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (mapsAiGuideState.text.isNotBlank()) {
                        Text(
                            text = mapsAiGuideState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (mapsAiGuideState.sources.isNotEmpty()) {
                        GroundedSourceChipsRow(sources = mapsAiGuideState.sources)
                    }
                }
            }
        }

        // 3. Nearby OSM Directory Results
        when (mosqueState) {
            is UiState.Loading -> {
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = "Scanning mosques, suraus, and prayer rooms…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Directory lookup unavailable: ${mosqueState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "You can still tap Maps or Ask Maps AI Nearby above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is UiState.Success -> {
                val filtered = if (selectedCategory == IbadahPlaceCategory.ALL) {
                    mosqueState.data
                } else {
                    mosqueState.data.filter { it.category == selectedCategory }
                }

                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No mapped places in this filter within ${selectedRadiusMeters / 1000} km.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Try expanding the radius to 10 km, opening Google Maps, or tapping Ask Maps AI Nearby.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { place ->
                        IbadahPlaceCard(
                            place = place,
                            onDirections = {
                                openGoogleMapsLocation(
                                    context = context,
                                    lat = place.latitude,
                                    lon = place.longitude,
                                    queryLabel = place.name
                                )
                            },
                            onAskAi = {
                                onAskAiInChat(
                                    "Tell me about ${place.name}" +
                                        (if (place.addressLine.isNotBlank()) " near ${place.addressLine}" else "") +
                                        " — prayer times, Friday Jumu'ah details, parking, or nearby facilities."
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IbadahPlaceCard(
    place: IbadahPlace,
    onDirections: () -> Unit,
    onAskAi: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        text = place.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = place.formattedDistance,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (place.addressLine.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = place.addressLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (place.facilityTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    place.facilityTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAskAi) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask AI")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = onDirections) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroundedSourceChipsRow(sources: List<GroundedSourceLink>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (sources.any { it.isMapsPlace }) "Google Maps & Verified Sources" else "Verified Sources",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sources.forEach { src ->
                AssistChip(
                    onClick = { openExternalUrl(context, src.uri) },
                    label = {
                        Text(
                            text = src.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (src.isMapsPlace) Icons.Filled.Map else Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}
