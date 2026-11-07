package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ReliabilityHelper
import com.example.i18n.Translations
import com.example.rememberAlarmReliability
import com.example.ui.theme.PrimaryBlue

private val OkGreen = Color(0xFF2E7D32)
private val AttentionOrange = Color(0xFFE65100)

private data class PermissionStep(
    val title: String,
    val openSettings: () -> Unit
)

@Composable
fun ReliabilityWizardDialog(
    strings: Translations,
    onDismiss: () -> Unit,
    onTestAlarm: () -> Unit
) {
    val context = LocalContext.current
    val reliability = rememberAlarmReliability()
    var showDndHelp by remember { mutableStateOf(false) }

    val checks = buildList {
        add(reliability.notificationsEnabled)
        if (reliability.notificationsEnabled) {
            add(reliability.alertChannelOk)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(reliability.fullScreenAllowed)
        }
        add(reliability.batteryUnrestricted)
    }
    val total = checks.size
    val done = checks.count { it }

    val missingSteps = buildList {
        if (!reliability.notificationsEnabled) {
            add(
                PermissionStep(strings.reliabilityNotifications) {
                    ReliabilityHelper.openNotificationSettings(context)
                }
            )
        } else if (!reliability.alertChannelOk) {
            add(
                PermissionStep(strings.reliabilityAlertChannel) {
                    ReliabilityHelper.openAlertChannelSettings(context)
                }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !reliability.fullScreenAllowed
        ) {
            add(
                PermissionStep(strings.reliabilityFullScreen) {
                    ReliabilityHelper.openFullScreenIntentSettings(context)
                }
            )
        }
        if (!reliability.batteryUnrestricted) {
            add(
                PermissionStep(strings.reliabilityBattery) {
                    ReliabilityHelper.openBatterySettings(context)
                }
            )
        }
    }
    val currentStep = missingSteps.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = PrimaryBlue
            )
        },
        title = { Text(strings.reliabilityWizardTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (currentStep == null) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = if (currentStep == null) OkGreen else AttentionOrange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.reliabilityWizardProgress.format(done, total),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStep == null) OkGreen else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (currentStep == null) {
                    Text(
                        text = strings.reliabilityWizardAllDone,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = strings.reliabilityTestHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.reliabilityWizardReturnHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                TextButton(
                    onClick = { showDndHelp = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${strings.reliabilityDnd} (${strings.reliabilityOptional})",
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            if (currentStep != null) {
                Button(onClick = { currentStep.openSettings() }) {
                    Text(strings.reliabilityOpenSettings)
                }
            } else {
                Button(onClick = onTestAlarm) {
                    Text(strings.reliabilityTestButton)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.reliabilityDone)
            }
        }
    )

    if (showDndHelp) {
        DndHelpDialog(
            strings = strings,
            onDismiss = { showDndHelp = false }
        )
    }
}

@Composable
fun DndHelpDialog(
    strings: Translations,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = PrimaryBlue
            )
        },
        title = { Text(strings.reliabilityDnd) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.reliabilityDndNotNeeded,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.reliabilityDndWhy,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = strings.reliabilityDndSteps,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = strings.reliabilityDndSamsung,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    ReliabilityHelper.openDndAccessSettings(context)
                }
            ) {
                Text(strings.reliabilityOpenSettings)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.reliabilitySkip)
            }
        }
    )
}
