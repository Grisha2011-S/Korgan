package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.BleStatus
import com.example.i18n.AppStrings
import com.example.ui.MainViewModel
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

    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
    val scanPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(90.dp)
                ) {
                    if (bleStatus == BleStatus.SCANNING) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(scanPulseScale)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f))
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                when (bleStatus) {
                                    BleStatus.CONNECTED, BleStatus.MONITORING -> StatusConnected
                                    BleStatus.SCANNING, BleStatus.CONNECTING -> StatusScanning
                                    BleStatus.DISCONNECTED -> MaterialTheme.colorScheme.primary
                                }
                            )
                    ) {
                        Icon(
                            imageVector = when (bleStatus) {
                                BleStatus.CONNECTED, BleStatus.MONITORING -> Icons.Default.BluetoothConnected
                                BleStatus.SCANNING -> Icons.Default.BluetoothSearching
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = when (bleStatus) {
                        BleStatus.CONNECTED, BleStatus.MONITORING -> strings.statusEsp32Connected
                        BleStatus.CONNECTING -> strings.statusEsp32Connecting
                        BleStatus.SCANNING -> strings.statusEsp32Scanning
                        BleStatus.DISCONNECTED -> strings.bleSearchAndPairTitle
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (bleStatus == BleStatus.SCANNING) {
                    Button(
                        onClick = { viewModel.stopBleScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(strings.stopScanButton)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startBleScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_ble_scan_button")
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.scanButton, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (settings.savedBleAddress.isNotBlank()) {
            val isSavedConnected = isConnected && settings.savedBleAddress == connectedAddress

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.deviceNameLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = settings.savedBleName.ifBlank { "ESP32-C3" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = settings.savedBleAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.clearSavedBle() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Forget",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (isSavedConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectBle() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.disconnectButton, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.connectToSavedAddress(settings.savedBleAddress) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.statusEsp32Connecting, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.BluetoothConnected, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.quickConnectButton, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-Reconnect BLE",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = settings.autoReconnect,
                            onCheckedChange = { viewModel.updateAutoReconnect(it) }
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = "${strings.bleSearchAndPairTitle} (${discoveredDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (bleStatus == BleStatus.SCANNING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }

                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (bleStatus == BleStatus.SCANNING)
                                strings.statusEsp32Scanning
                            else
                                strings.noBleDevicesFound,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    discoveredDevices.forEach { dev ->
                        val isCurrentConnected = dev.address == connectedAddress && isConnected

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrentConnected)
                                StatusConnected.copy(alpha = 0.12f)
                            else if (dev.isEsp32)
                                PrimaryBlue.copy(alpha = 0.08f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = if (dev.isEsp32)
                                androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                            else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = dev.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (dev.isEsp32) {
                                            Surface(
                                                color = PrimaryBlue,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ESP32",
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${dev.address} • ${dev.rssi} dBm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isCurrentConnected) {
                                    FilledTonalButton(
                                        onClick = { viewModel.disconnectBle() },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Text(strings.disconnectButton, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.connectToDevice(dev.device, dev.name, dev.address) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(38.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (dev.isEsp32) PrimaryBlue else MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Text(strings.connectDeviceButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
