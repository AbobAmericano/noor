package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SavedVerse
import com.example.data.repository.InspirationRepository
import com.example.ui.UiState
import com.example.ui.components.openExternalUrl
import com.example.ui.components.shareQuoteText
import com.example.ui.theme.ArabicVerseTextStyle
import com.example.ui.theme.QuoteEditorialTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveResearchScreen(
    savedVersesState: UiState<List<SavedVerse>>,
    onUpdateNote: (String, String) -> Unit,
    onDeleteVerse: (String) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = Archive, 1 = Research
    var searchQuery by remember { mutableStateOf("") }
    var archiveFilter by remember { mutableStateOf<String?>(null) }
    var editingVerse by remember { mutableStateOf<SavedVerse?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var researchCategory by remember { mutableStateOf<String?>(null) }

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
                selected = subTab == 0,
                onClick = { subTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("archive_subtab_saved")
            ) {
                Text("Saved Archive")
            }
            SegmentedButton(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("archive_subtab_research")
            ) {
                Text("Research & Tafsir")
            }
        }

        if (subTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search verses, references, or reflections…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = archiveFilter == null,
                                onClick = { archiveFilter = null },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = archiveFilter == "QURAN",
                                onClick = { archiveFilter = "QURAN" },
                                label = { Text("Al-Quran") }
                            )
                            FilterChip(
                                selected = archiveFilter == "HADITH",
                                onClick = { archiveFilter = "HADITH" },
                                label = { Text("Hadith") }
                            )
                        }
                    }
                }

                when (savedVersesState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is UiState.Error -> {
                        item {
                            Surface(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = savedVersesState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        val filtered = savedVersesState.data.filter { item ->
                            val matchesType = archiveFilter == null || item.sourceType == archiveFilter
                            val q = searchQuery.trim().lowercase()
                            val matchesQuery = q.isBlank() ||
                                item.reference.lowercase().contains(q) ||
                                item.translation.lowercase().contains(q) ||
                                item.reflectionNote.lowercase().contains(q)
                            matchesType && matchesQuery
                        }

                        if (filtered.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.BookmarkBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "Your reflection archive is empty",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Tap Save on any daily Quran verse or Hadith to preserve it here with your personal notes.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filtered, key = { it.id }) { verse ->
                                Surface(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${verse.sourceType} • ${verse.reference}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(onClick = { onDeleteVerse(verse.id) }) {
                                                Icon(
                                                    Icons.Filled.DeleteOutline,
                                                    contentDescription = "Remove saved verse",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (verse.arabicText.isNotBlank()) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                Text(
                                                    text = verse.arabicText,
                                                    style = ArabicVerseTextStyle,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }

                                        Text(
                                            text = "“${verse.translation}”",
                                            style = QuoteEditorialTextStyle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (verse.reflectionNote.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "Reflection: ${verse.reflectionNote}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    editingVerse = verse
                                                    noteDraft = verse.reflectionNote
                                                }
                                            ) {
                                                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (verse.reflectionNote.isBlank()) "Reflect" else "Edit Note")
                                            }

                                            TextButton(
                                                onClick = {
                                                    shareQuoteText(
                                                        context,
                                                        verse.reference,
                                                        verse.arabicText,
                                                        verse.translation,
                                                        verse.sourceUrl
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Share")
                                            }

                                            TextButton(
                                                onClick = {
                                                    openExternalUrl(context, verse.tafsirUrl.ifBlank { verse.sourceUrl })
                                                }
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tafsir")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val categories = listOf("Tafsir & Exegesis", "Hadith & Sciences", "JAKIM Malaysia", "Academic Research")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = researchCategory == null,
                                onClick = { researchCategory = null },
                                label = { Text("All Sources") }
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = researchCategory == cat,
                                onClick = { researchCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                val resources = InspirationRepository.researchResources.filter {
                    researchCategory == null || it.category == researchCategory
                }

                items(resources, key = { it.id }) { res ->
                    Surface(
                        onClick = { openExternalUrl(context, res.url) },
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${res.category.uppercase()} • ${res.domainBadge}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = res.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = res.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open ${res.title}",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    val verseToEdit = editingVerse
    if (verseToEdit != null) {
        AlertDialog(
            onDismissRequest = { editingVerse = null },
            title = { Text("Personal Reflection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = verseToEdit.reference,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Write your reflection…") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateNote(verseToEdit.id, noteDraft.trim())
                        editingVerse = null
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingVerse = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
