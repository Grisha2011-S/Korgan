package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonalInjury
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Group as GroupOutlined
import androidx.compose.material.icons.outlined.NotificationsActive as NotificationsActiveOutlined
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloud.CaregiverAlertActivity
import com.example.cloud.FamilyCloud
import com.example.cloud.PhoneRole
import com.example.cloud.RemoteAlert
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.FamilyStrings
import com.example.i18n.FamilyText
import com.example.ui.MainViewModel
import com.example.ui.components.IconBadge
import com.example.ui.components.SectionCard
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.GuardianTeal
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val INBOX_POLL_MS = 10_000L
private const val FRESH_ALERT_MS = 10 * 60_000L

private data class CaregiverTab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Всё приложение в режиме «Я слежу за близким». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverAppScreen(viewModel: MainViewModel) {
    val cloud = viewModel.familyCloud
    val state by cloud.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val text = FamilyText.get(settings.appLanguage)
    val common = AppStrings.get(settings.appLanguage)

    if (!state.registered) {
        CaregiverSetupScreen(cloud, text, onBack = { cloud.setRole(PhoneRole.UNSET) })
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    InboxPolling(cloud, pushAvailable = state.pushAvailable)

    val tabs = listOf(
        CaregiverTab(text.tabAlerts, Icons.Outlined.NotificationsActiveOutlined, Icons.Default.NotificationsActive),
        CaregiverTab(text.tabPeople, Icons.Outlined.GroupOutlined, Icons.Default.Group),
        CaregiverTab(common.tabSettings, Icons.Outlined.SettingsOutlined, Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(common.appTitle, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                imageVector = if (tab == index) item.selectedIcon else item.icon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                item.title,
                                fontSize = 11.sp,
                                fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> AlertsTab(cloud, text)
                1 -> PeopleTab(cloud, text)
                else -> CaregiverSettingsTab(viewModel, cloud, text, common.languageSectionTitle)
            }
        }
    }
}

/**
 * Пока экран открыт, опрашивает сервер. Без настроенного push это единственный
 * способ узнать о тревоге, поэтому свежая тревога сразу открывается на весь экран.
 */
@Composable
private fun InboxPolling(cloud: FamilyCloud, pushAvailable: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(cloud) {
        val seen = mutableSetOf<String>()
        var first = true
        while (true) {
            cloud.refreshInbox().onSuccess { inbox ->
                val now = System.currentTimeMillis()
                inbox.filter { it.id !in seen }.forEach { alert ->
                    seen += alert.id
                    val fresh = now - alert.createdMs < FRESH_ALERT_MS && alert.ackedByName == null
                    if (!first && fresh && !pushAvailable) CaregiverAlertActivity.show(context, alert)
                }
                first = false
            }
            delay(INBOX_POLL_MS)
        }
    }
}

@Composable
private fun CaregiverSetupScreen(cloud: FamilyCloud, text: FamilyStrings, onBack: () -> Unit) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.myName) }
    var url by remember { mutableStateOf(state.serverUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        IconBadge(icon = Icons.Outlined.VerifiedUser, tint = PrimaryBlue, size = 88.dp)
        Text(
            text.caregiverSetupTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            placeholder = { Text(text.yourNameLabel) },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            placeholder = { Text(text.serverUrlLabel) },
            leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { ErrorLine(it) }
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    cloud.connect(url, name).onFailure { error = text.serverError.format(it.message ?: "") }
                    busy = false
                }
            },
            enabled = !busy && name.isNotBlank() && url.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (busy) text.connecting else text.connectButton, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onBack) { Text(text.backButton) }
    }
}

@Composable
private fun AlertsTab(cloud: FamilyCloud, text: FamilyStrings) {
    val context = LocalContext.current
    val state by cloud.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
    }
    LaunchedEffect(Unit) {
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Статус связи и push — одна строка
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) { ServerStatusRow(state.registered, state.online, text) }
            Icon(
                imageVector = if (state.pushAvailable) Icons.Outlined.NotificationsActiveOutlined else Icons.Outlined.NotificationsOff,
                contentDescription = if (state.pushAvailable) text.pushReady else text.pushMissing,
                tint = if (state.pushAvailable) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (!notificationsAllowed) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text.notificationsOffWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(text.allowButton) }
                }
            }
        }
    }

    if (state.inbox.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon = Icons.Outlined.VerifiedUser, tint = StatusConnected, size = 88.dp)
            Text(
                text.noAlerts,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
    state.inbox.forEach { alert ->
        AlertCard(alert, text,
            onAck = { scope.launch { cloud.ack(alert.id) } },
            onMap = { CaregiverAlertActivity.openMap(context, alert) })
    }
}

@Composable
private fun AlertCard(alert: RemoteAlert, text: FamilyStrings, onAck: () -> Unit, onMap: () -> Unit) {
    val isFresh = System.currentTimeMillis() - alert.createdMs < FRESH_ALERT_MS
    val accent = when {
        alert.ackedByName != null -> StatusConnected
        alert.kind == "TEST" -> PrimaryBlue
        else -> EmergencyRed
    }
    val icon = when (alert.kind) {
        "SOS" -> Icons.Outlined.Sos
        "TEST" -> Icons.Outlined.Science
        else -> Icons.Outlined.PersonalInjury
    }
    val urgent = alert.ackedByName == null && isFresh && alert.kind != "TEST"

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (urgent) BorderStroke(1.5.dp, accent) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(icon = icon, tint = accent, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        when (alert.kind) {
                            "SOS" -> text.alertSos
                            "TEST" -> text.alertTest
                            else -> text.alertFall
                        }.format(alert.wearerName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(alert.createdMs)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                alert.battery?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.BatteryStd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("$it%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (alert.address.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        alert.address,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    alert.ackedByName != null -> Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = StatusConnected, modifier = Modifier.size(18.dp))
                        Text(
                            text.ackedBy.format(alert.ackedByName),
                            color = StatusConnected,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    urgent -> Button(
                        onClick = onAck,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(text.ackButton, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    else -> Spacer(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = onMap,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text.openMap, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(cloud: FamilyCloud, text: FamilyStrings) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            cloud.refreshLinks()
            delay(INBOX_POLL_MS)
        }
    }

    SectionCard(icon = Icons.Outlined.PersonAdd, title = text.addPersonTitle) {
        Text(
            text.addPersonHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        code?.let {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        it.chunked(3).joinToString(" "),
                        fontSize = 40.sp, fontWeight = FontWeight.Black, color = PrimaryBlue
                    )
                    Text(
                        text.codeValidFor,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        error?.let { ErrorLine(it) }
        Button(
            onClick = {
                scope.launch {
                    cloud.requestPairCode()
                        .onSuccess { code = it; error = null }
                        .onFailure { error = text.serverError.format(it.message ?: "") }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text.getCodeButton, fontWeight = FontWeight.SemiBold)
        }
    }

    SectionCard(icon = Icons.Outlined.GroupOutlined, title = text.linkedPeopleTitle, tint = GuardianTeal) {
        if (state.links.isEmpty()) {
            Text(
                text.noLinkedPeople,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.links.forEach { link ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = GuardianTeal, modifier = Modifier.size(20.dp))
                    Text(link.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { scope.launch { cloud.removeLink(link.deviceId) } }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = text.removeLink,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverSettingsTab(viewModel: MainViewModel, cloud: FamilyCloud, text: FamilyStrings, languageTitle: String) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember(state.myName) { mutableStateOf(state.myName) }
    var url by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard(icon = Icons.Outlined.Cloud, title = text.serverUrlLabel) {
        ServerStatusRow(state.registered, state.online, text)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) },
            placeholder = { Text(text.yourNameLabel) },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            placeholder = { Text(text.serverUrlLabel) },
            leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { ErrorLine(it) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    cloud.connect(url, name)
                        .onSuccess { error = null }
                        .onFailure { error = text.serverError.format(it.message ?: "") }
                    busy = false
                }
            },
            enabled = !busy && name.isNotBlank() && url.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (busy) text.connecting else text.connectButton, fontWeight = FontWeight.SemiBold) }
    }

    SectionCard(icon = Icons.Outlined.Language, title = languageTitle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLanguage.entries.forEach { lang ->
                val selected = settings.appLanguage.equals(lang.code, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = if (selected) BorderStroke(1.5.dp, PrimaryBlue) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.updateAppLanguage(lang.code) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = lang.flag, fontSize = 22.sp)
                        Text(
                            text = lang.code.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    OutlinedButton(
        onClick = { cloud.setRole(PhoneRole.UNSET) },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text.changeRole)
    }
}

@Composable
private fun ErrorLine(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
        Text(message, color = EmergencyRed, style = MaterialTheme.typography.bodySmall)
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
