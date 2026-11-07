package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalCellularAlt1Bar
import androidx.compose.material.icons.outlined.SignalCellularAlt2Bar
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.BleStatus
import com.example.i18n.AppStrings
import com.example.ui.MainViewModel
import com.example.ui.components.DeviceIllustration
import com.example.ui.components.IconBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.ToggleRow
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusScanning

@Composable
fun BleDevicesScreen(
    viewModel: MainViewModel
) {
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connectedAddress by viewModel.connectedDeviceAddress.collectAsStateWithLifecycle()
    val strings = AppStrings.get(settings.appLanguage)

    val isConnected = bleStatus == BleStatus.CONNECTED || bleStatus == BleStatus.MONITORING
    val isConnecting = bleStatus == BleStatus.CONNECTING
    val isScanning = bleStatus == BleStatus.SCANNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Поиск ────────────────────────────────────────────────────
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
                    color = when {
                        isConnected -> StatusConnected
                        isScanning || isConnecting -> StatusScanning
                        else -> PrimaryBlue
                    },
                    active = isConnected,
                    searching = isScanning || isConnecting
                )
                Text(
                    text = when (bleStatus) {
                        BleStatus.CONNECTED, BleStatus.MONITORING -> strings.statusEsp32Connected
                        BleStatus.CONNECTING -> strings.statusEsp32Connecting
                        BleStatus.SCANNING -> strings.statusEsp32Scanning
                        BleStatus.DISCONNECTED -> strings.statusEsp32Disconnected
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isScanning) {
                    FilledTonalButton(
                        onClick = { viewModel.stopBleScan() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        BleButtonContent(Icons.Outlined.StopCircle, strings.stopScanButton)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startBleScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_ble_scan_button")
                    ) {
                        BleButtonContent(Icons.Outlined.BluetoothSearching, strings.scanButton)
                    }
                }
            }
        }

        // ── Сохранённый датчик ───────────────────────────────────────
        if (settings.savedBleAddress.isNotBlank()) {
            val isSavedConnected = isConnected && settings.savedBleAddress == connectedAddress

            SectionCard(
                icon = Icons.Outlined.Sensors,
                title = settings.savedBleName.ifBlank { "ESP32-C3" },
                tint = if (isSavedConnected) StatusConnected else PrimaryBlue,
                action = {
                    IconButton(onClick = { viewModel.clearSavedBle() }) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Forget",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            ) {
                if (isSavedConnected) {
                    OutlinedButton(
                        onClick = { viewModel.disconnectBle() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        BleButtonContent(Icons.Outlined.LinkOff, strings.disconnectButton)
                    }
                } else {
                    Button(
                        onClick = { viewModel.connectToSavedAddress(settings.savedBleAddress) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.statusEsp32Connecting)
                        } else {
                            BleButtonContent(Icons.Outlined.Sensors, strings.quickConnectButton)
                        }
                    }
                }

                ToggleRow(
                    icon = Icons.Outlined.Autorenew,
                    title = "Auto-Reconnect",
                    checked = settings.autoReconnect,
                    onCheckedChange = { viewModel.updateAutoReconnect(it) }
                )
            }
        }

        // ── Найденные устройства ─────────────────────────────────────
        SectionCard(
            icon = Icons.Outlined.Bluetooth,
            title = if (discoveredDevices.isEmpty()) strings.bleSearchAndPairTitle
            else "${strings.bleSearchAndPairTitle} · ${discoveredDevices.size}",
            action = if (isScanning) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null
        ) {
            if (discoveredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Outlined.BluetoothSearching else Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isScanning) strings.statusEsp32Scanning else strings.noBleDevicesFound,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                discoveredDevices.forEach { dev ->
                    val isCurrentConnected = dev.address == connectedAddress && isConnected
                    val tint = when {
                        isCurrentConnected -> StatusConnected
                        dev.isEsp32 -> PrimaryBlue
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (dev.isEsp32 || isCurrentConnected) tint.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (dev.isEsp32) BorderStroke(1.dp, tint.copy(alpha = 0.4f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconBadge(
                                icon = if (dev.isEsp32) Icons.Outlined.Watch else Icons.Outlined.Bluetooth,
                                tint = tint,
                                size = 36.dp
                            )
                            Text(
                                text = dev.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = signalIcon(dev.rssi),
                                contentDescription = "${dev.rssi} dBm",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            if (isCurrentConnected) {
                                IconButton(onClick = { viewModel.disconnectBle() }) {
                                    Icon(
                                        Icons.Outlined.LinkOff,
                                        contentDescription = strings.disconnectButton,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { viewModel.connectToDevice(dev.device, dev.name, dev.address) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text(strings.connectDeviceButton, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun signalIcon(rssi: Int): ImageVector = when {
    rssi >= -60 -> Icons.Outlined.SignalCellularAlt
    rssi >= -80 -> Icons.Outlined.SignalCellularAlt2Bar
    else -> Icons.Outlined.SignalCellularAlt1Bar
}

@Composable
private fun BleButtonContent(icon: ImageVector, text: String) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
