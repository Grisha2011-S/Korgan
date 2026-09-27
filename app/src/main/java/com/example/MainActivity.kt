package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ble.BleStatus
import com.example.i18n.AppStrings
import com.example.ui.MainViewModel
import com.example.ui.components.LanguageOnboardingDialog
import com.example.ui.screens.BleDevicesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusScanning

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

data class NavTabItem(val title: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val strings = AppStrings.get(settings.appLanguage)

    val navTabs = listOf(
        NavTabItem(strings.tabHome, Icons.Default.Home),
        NavTabItem(strings.tabBle, Icons.Default.Bluetooth),
        NavTabItem(strings.tabSettings, Icons.Default.Settings),
        NavTabItem(strings.tabHistory, Icons.Default.History)
    )

    var permissionsGranted by remember {
        mutableStateOf(checkAllPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsGranted = checkAllPermissions(context)
        if (permissionsGranted) {
            viewModel.refreshLocation()
            if (viewModel.settings.value.serviceEnabled) {
                com.example.service.FallDetectionService.startService(context)
            }
        }
    }

    LaunchedEffect(settings.hasCompletedLanguageSetup) {
        if (settings.hasCompletedLanguageSetup) {
            if (!permissionsGranted) {
                val permissionsToRequest = getRequiredPermissions()
                permissionLauncher.launch(permissionsToRequest)
            } else {
                if (viewModel.settings.value.serviceEnabled) {
                    com.example.service.FallDetectionService.startService(context)
                }
            }
        }
    }

    if (!settings.hasCompletedLanguageSetup) {
        LanguageOnboardingDialog(
            initialLanguageCode = settings.appLanguage,
            onConfirm = { selectedCode ->
                viewModel.updateAppLanguage(selectedCode)
                viewModel.markLanguageSetupCompleted()
                if (!permissionsGranted) {
                    permissionLauncher.launch(getRequiredPermissions())
                } else {
                    if (viewModel.settings.value.serviceEnabled) {
                        com.example.service.FallDetectionService.startService(context)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (bleStatus) {
                                        BleStatus.CONNECTED, BleStatus.MONITORING -> StatusConnected
                                        BleStatus.CONNECTING, BleStatus.SCANNING -> StatusScanning
                                        BleStatus.DISCONNECTED -> StatusDisconnected
                                    }
                                )
                        )
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                navTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = !permissionsGranted && settings.hasCompletedLanguageSetup) {
                Card(
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = strings.permissionWarningBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(getRequiredPermissions())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.grantPermissionButton, fontSize = 12.sp)
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToBle = { selectedTab = 1 },
                    onNavigateToSettings = { selectedTab = 2 }
                )
                1 -> BleDevicesScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
                3 -> HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

private fun getRequiredPermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        list.add(Manifest.permission.BLUETOOTH)
        list.add(Manifest.permission.BLUETOOTH_ADMIN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.toTypedArray()
}

private fun checkAllPermissions(context: android.content.Context): Boolean {
    val permissions = getRequiredPermissions()
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
