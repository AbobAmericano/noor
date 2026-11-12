package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiChatMode
import com.example.data.ai.GroundedSourceLink
import com.example.domain.solat.SolatSchedule
import com.example.ui.AskAiUiState
import com.example.ui.ChatMessageUi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiScreen(
    uiState: AskAiUiState,
    solatSchedule: SolatSchedule,
    onDraftChange: (String) -> Unit,
    onSelectMode: (AiChatMode) -> Unit,
    onSendMessage: (String) -> Unit,
    onResetChat: () -> Unit
) {
    val listState = rememberLazyListState()

    val itemCount = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
    LaunchedEffect(itemCount, uiState.streamingText.length) {
        if (itemCount > 0) {
            listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Role & Model Mode Selector Header
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Noor AI Companion",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.selectedMode.subtitle} • ${solatSchedule.locationLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onResetChat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ask_ai_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = "Start new AI conversation"
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AiChatMode.entries, key = { it.name }) { mode ->
                        FilterChip(
                            selected = uiState.selectedMode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (mode.usesMapsGrounding) Icons.Filled.Map else Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Conversation Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Assalamu'alaikum. Ask anything about Quran verses, Hadith, Solat, or nearby mosques.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Powered securely by Firebase AI Logic (${uiState.selectedMode.modelName}) with Google Maps grounding — no API key needed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val prompts = listOf(
                                "Find the nearest mosque or surau to pray around ${solatSchedule.locationLabel}",
                                "Which mosques near me have Jumu'ah prayer or a women's prayer space?",
                                "Explain the sunnah & virtues of sending Salawat upon the Prophet",
                                "How do Jamak and Qasar prayers work when traveling?"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                prompts.forEachIndexed { idx, suggestion ->
                                    AssistChip(
                                        onClick = { onSendMessage(suggestion) },
                                        label = { Text(suggestion) },
                                        modifier = Modifier.testTag("ask_ai_starter_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(uiState.messages, key = { it.id }) { msg ->
                ChatBubbleItem(message = msg)
            }

            if (uiState.isGenerating) {
                item(key = "streaming_bubble") {
                    StreamingBubbleItem(
                        partialText = uiState.streamingText,
                        sources = uiState.streamingSources,
                        modeLabel = uiState.selectedMode.label
                    )
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val lastUserPrompt = uiState.messages.lastOrNull { it.isUser }?.text
                            if (!lastUserPrompt.isNullOrBlank()) {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onSendMessage(lastUserPrompt) }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Message Composer
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.draftInput,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ask_ai_input"),
                    placeholder = {
                        Text(
                            if (uiState.selectedMode.usesMapsGrounding) {
                                "Ask about nearby mosques, suraus, facilities…"
                            } else {
                                "Ask about Quran, Hadith, Solat, or Dhikr…"
                            }
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!uiState.isGenerating && uiState.draftInput.isNotBlank()) {
                                onSendMessage(uiState.draftInput)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                IconButton(
                    onClick = { onSendMessage(uiState.draftInput) },
                    enabled = !uiState.isGenerating && uiState.draftInput.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ask_ai_send_button")
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send question to AI"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(message: ChatMessageUi) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (message.isUser) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.isUser && message.modeLabel.isNotBlank()) {
                    Text(
                        text = message.modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (!message.isUser && message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    GroundedSourceChipsRow(sources = message.sources)
                }
            }
        }
    }
}

@Composable
private fun StreamingBubbleItem(
    partialText: String,
    sources: List<GroundedSourceLink>,
    modeLabel: String
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = "$modeLabel • Thinking…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (partialText.isNotBlank()) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (sources.isNotEmpty()) {
                    GroundedSourceChipsRow(sources = sources)
                }
            }
        }
    }
}
