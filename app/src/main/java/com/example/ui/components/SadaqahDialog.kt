package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billing.SadaqahBillingManager
import com.example.billing.SadaqahFlowState

@Composable
fun SadaqahSupportDialog(
    onCloseWithBlessing: () -> Unit,
    onCompleted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val billingManager = remember(context) { SadaqahBillingManager(context, scope) }
    val flowState by billingManager.state.collectAsStateWithLifecycle()
    val formattedPrice by billingManager.formattedPrice.collectAsStateWithLifecycle()

    LaunchedEffect(billingManager) {
        billingManager.warmUp()
    }

    DisposableEffect(billingManager) {
        onDispose { billingManager.release() }
    }

    AlertDialog(
        onDismissRequest = onCloseWithBlessing,
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.VolunteerActivism,
                    contentDescription = "Support developer with Sadaqah",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Give Sadaqah ($formattedPrice)",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val state = flowState) {
                    is SadaqahFlowState.Idle -> {
                        Text(
                            text = "Noor is free and ad-free. If this companion benefits your daily ibadah, consider supporting the developer with a voluntary RM 2 (2 MYR) Sadaqah — or close with a du'a.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SadaqahFlowState.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Connecting to Google Play Billing…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is SadaqahFlowState.Success -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is SadaqahFlowState.SetupRequired -> {
                        Text(
                            text = state.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SadaqahFlowState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val state = flowState) {
                    is SadaqahFlowState.Success -> {
                        Button(
                            onClick = { onCompleted(state.message) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sadaqah_done_button")
                        ) {
                            Text("Ameen • Close")
                        }
                    }

                    is SadaqahFlowState.SetupRequired -> {
                        TextButton(
                            onClick = onCloseWithBlessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sadaqah_bless_close_button")
                        ) {
                            Text("May Allah bless developer")
                        }
                    }

                    else -> {
                        Button(
                            onClick = { billingManager.startSadaqahTip(activity) },
                            enabled = flowState !is SadaqahFlowState.Connecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sadaqah_give_tip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolunteerActivism,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Give Sadaqah ($formattedPrice)")
                        }

                        TextButton(
                            onClick = onCloseWithBlessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sadaqah_bless_close_button")
                        ) {
                            Text("May Allah bless developer")
                        }
                    }
                }
            }
        }
    )
}
