package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.BleManager
import com.example.ble.BleStatus
import com.example.dispatcher.AlertSource
import com.example.i18n.AppStrings
import com.example.rememberAlarmReliability
import com.example.ui.MainViewModel
import com.example.ui.components.DeviceIllustration
import com.example.ui.components.IconBadge
import com.example.ui.components.InfoTile
import com.example.ui.theme.GuardianTeal
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusScanning

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToBle: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val deviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val signalRssi by viewModel.signalRssi.collectAsStateWithLifecycle()
    val locationInfo by viewModel.locationInfo.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val deviceError by viewModel.deviceError.collectAsStateWithLifecycle()
    val strings = AppStrings.get(settings.appLanguage)
    val validContacts = settings.validContacts
    val reliability = rememberAlarmReliability()

    val isConnected = bleStatus == BleStatus.CONNECTED || bleStatus == BleStatus.MONITORING
    val isConnecting = bleStatus == BleStatus.CONNECTING || bleStatus == BleStatus.SCANNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Статус датчика ────────────────────────────────────────────
        val (statusColor, statusLabel) = when (bleStatus) {
            BleStatus.CONNECTED, BleStatus.MONITORING -> StatusConnected to strings.statusEsp32Connected
            BleStatus.CONNECTING -> StatusScanning to strings.statusEsp32Connecting
            BleStatus.SCANNING -> StatusScanning to strings.statusEsp32Scanning
            BleStatus.DISCONNECTED -> StatusDisconnected to strings.statusEsp32Disconnected
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceIllustration(
                    color = statusColor,
                    active = isConnected,
                    searching = isConnecting
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                val subtitle = when {
                    isConnected && deviceName != null ->
                        "$deviceName${signalRssi?.let { " · $it dBm" } ?: ""}"
                    settings.savedBleAddress.isNotBlank() ->
                        settings.savedBleName.ifBlank { "ESP32-C3" }
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isConnected -> OutlinedButton(
                        onClick = { viewModel.disconnectBle() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("disconnect_esp32_button")
                    ) {
                        ButtonContent(Icons.Outlined.LinkOff, strings.disconnectButton)
                    }

                    isConnecting -> FilledTonalButton(
                        onClick = { viewModel.disconnectBle() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(strings.stopScanButton)
                    }

                    settings.savedBleAddress.isNotBlank() -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.connectToSavedAddress(settings.savedBleAddress) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("connect_saved_esp32_button")
                        ) {
                            ButtonContent(Icons.Outlined.Sensors, strings.quickConnectButton)
                        }
                        FilledTonalIconButton(
                            onClick = onNavigateToBle,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("scan_other_esp32_button")
                        ) {
                            Icon(Icons.Outlined.BluetoothSearching, contentDescription = strings.scanButton)
                        }
                    }

                    else -> Button(
                        onClick = onNavigateToBle,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("connect_esp32_button")
                    ) {
                        ButtonContent(Icons.Outlined.BluetoothSearching, strings.bleSearchAndPairTitle)
                    }
                }
            }
        }

        // ── Предупреждения ────────────────────────────────────────────
        if (!reliability.criticalOk) {
            WarningBanner(
                title = strings.reliabilityTitle,
                onClick = onNavigateToSettings
            )
        }
        if (deviceError == BleManager.DEVICE_ERROR_SENSOR) {
            WarningBanner(
                title = strings.sensorErrorTitle,
                onDismiss = { viewModel.clearDeviceError() }
            )
        }

        // ── Профиль и контакты ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoTile(
                icon = Icons.Outlined.Person,
                tint = PrimaryBlue,
                value = settings.userName.ifBlank { "—" },
                label = strings.profileSectionTitle,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                icon = Icons.Outlined.Group,
                tint = if (validContacts.isEmpty()) MaterialTheme.colorScheme.error else GuardianTeal,
                value = if (validContacts.isEmpty()) "0" else validContacts.size.toString(),
                label = strings.contactsSectionTitle,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Местоположение ────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(icon = Icons.Outlined.Place, tint = GuardianTeal, size = 36.dp)
                Text(
                    text = locationInfo.address.ifBlank { strings.addressSearching },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.refreshLocation() }) {
                    if (locationInfo.isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = strings.locationTitle)
                    }
                }
                if (locationInfo.latitude != 0.0) {
                    IconButton(onClick = {
                        val uri = Uri.parse("geo:${locationInfo.latitude},${locationInfo.longitude}?q=${locationInfo.latitude},${locationInfo.longitude}(Fall)")
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Icon(Icons.Outlined.Map, contentDescription = "Maps")
                    }
                }
            }
        }

        // ── Превью SMS (свёрнуто по умолчанию) ────────────────────────
        var smsExpanded by remember { mutableStateOf(false) }
        val arrowRotation by animateFloatAsState(if (smsExpanded) 180f else 0f, label = "arrow")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { smsExpanded = !smsExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(icon = Icons.AutoMirrored.Outlined.Chat, tint = PrimaryBlue, size = 36.dp)
                    Text(
                        text = strings.smsPreviewLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
                AnimatedVisibility(visible = smsExpanded) {
                    val fullSms = viewModel.dispatcher.buildSmsMessage(
                        source = AlertSource.ESP32_FALL,
                        userName = settings.userName,
                        address = locationInfo.address,
                        latitude = locationInfo.latitude,
                        longitude = locationInfo.longitude,
                        accuracyMeters = locationInfo.accuracy,
                        customTemplate = settings.customTemplate,
                        languageCode = settings.appLanguage
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = fullSms,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Короткая плашка-предупреждение в одну строку: по нажатию ведёт к исправлению или закрывается. */
@Composable
private fun WarningBanner(
    title: String,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
