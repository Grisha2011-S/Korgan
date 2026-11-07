package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ReliabilityHelper
import com.example.data.EmergencyContact
import com.example.data.isValidPhoneNumber
import com.example.dispatcher.AlertSource
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.rememberAlarmReliability
import com.example.ui.MainViewModel
import com.example.ui.components.SectionCard
import com.example.ui.components.ToggleRow
import com.example.ui.theme.GuardianTeal
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locationInfo by viewModel.locationInfo.collectAsStateWithLifecycle()
    val strings = AppStrings.get(settings.appLanguage)

    var nameInput by remember(settings.userName) { mutableStateOf(settings.userName) }
    var templateInput by remember(settings.customTemplate) { mutableStateOf(settings.customTemplate) }

    // Локальная копия нужна, чтобы пустая строка нового получателя не исчезала
    // в момент, когда пользователь ещё ничего не ввёл.
    var contacts by remember {
        mutableStateOf(
            settings.emergencyContacts.ifEmpty { listOf(EmergencyContact()) }
        )
    }

    fun publishContacts(updated: List<EmergencyContact>) {
        contacts = updated
        viewModel.updateEmergencyContacts(updated)
    }

    val hasValidContact = contacts.any { isValidPhoneNumber(it.phone) }
    val hasInvalidPhone = contacts.any {
        it.phone.isNotBlank() && !isValidPhoneNumber(it.phone)
    }

    val context = LocalContext.current
    val reliability = rememberAlarmReliability()
    var showDndHelp by remember { mutableStateOf(false) }
    var showWizard by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Профиль ──────────────────────────────────────────────────
        SectionCard(icon = Icons.Outlined.Person, title = strings.profileSectionTitle) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    viewModel.updateUserName(it)
                },
                placeholder = { Text(strings.userNamePlaceholder) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_name_input")
            )
        }

        // ── Контакты ─────────────────────────────────────────────────
        SectionCard(
            icon = Icons.Outlined.Group,
            title = strings.contactsSectionTitle,
            tint = GuardianTeal,
            action = {
                IconButton(
                    onClick = { publishContacts(contacts + EmergencyContact()) },
                    modifier = Modifier.testTag("add_contact_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = strings.addContactButton, tint = GuardianTeal)
                }
            }
        ) {
            contacts.forEachIndexed { index, contact ->
                key(contact.id) {
                    val phoneInvalid = contact.phone.isNotBlank() && !isValidPhoneNumber(contact.phone)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp),
                        border = if (index == 0) BorderStroke(1.dp, GuardianTeal.copy(alpha = 0.5f)) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = contact.name,
                                    onValueChange = { newName ->
                                        publishContacts(
                                            contacts.map {
                                                if (it.id == contact.id) it.copy(name = newName) else it
                                            }
                                        )
                                    },
                                    placeholder = { Text(strings.contactNamePlaceholder) },
                                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_name_$index")
                                )
                                OutlinedTextField(
                                    value = contact.phone,
                                    onValueChange = { newPhone ->
                                        publishContacts(
                                            contacts.map {
                                                if (it.id == contact.id) it.copy(phone = newPhone) else it
                                            }
                                        )
                                    },
                                    placeholder = { Text(strings.contactPhonePlaceholder) },
                                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    isError = phoneInvalid,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_phone_$index")
                                )
                            }
                            IconButton(
                                onClick = {
                                    val remaining = contacts.filterNot { it.id == contact.id }
                                    publishContacts(remaining.ifEmpty { listOf(EmergencyContact()) })
                                },
                                modifier = Modifier.testTag("remove_contact_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = strings.removeContact,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (!hasValidContact || hasInvalidPhone) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (hasInvalidPhone) strings.profileSetupErrorPhone else strings.contactsRequiredWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── Способы оповещения ───────────────────────────────────────
        SectionCard(icon = Icons.Outlined.NotificationsActive, title = strings.alertMethodsSectionTitle) {
            ToggleRow(
                icon = Icons.Outlined.Sms,
                title = strings.sendSmsTitle,
                checked = settings.sendSms,
                onCheckedChange = { viewModel.updateSendSms(it) }
            )
            ToggleRow(
                icon = Icons.Outlined.PhoneInTalk,
                title = strings.callAndVoiceTitle,
                checked = settings.enableCallAndVoice,
                onCheckedChange = { viewModel.updateEnableCallAndVoice(it) }
            )
            AnimatedVisibility(visible = settings.enableCallAndVoice) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SliderRow(
                        icon = Icons.Outlined.Speed,
                        label = strings.robotVoiceSpeedLabel,
                        valueText = "%.1f×".format(settings.robotVoiceSpeed),
                        value = settings.robotVoiceSpeed,
                        range = 0.6f..1.5f,
                        onChange = { viewModel.updateVoiceSettings(it, settings.robotVoicePitch) }
                    )
                    SliderRow(
                        icon = Icons.Outlined.RecordVoiceOver,
                        label = strings.robotVoicePitchLabel,
                        valueText = "%.1f".format(settings.robotVoicePitch),
                        value = settings.robotVoicePitch,
                        range = 0.5f..1.5f,
                        onChange = { viewModel.updateVoiceSettings(settings.robotVoiceSpeed, it) }
                    )
                }
            }
            ToggleRow(
                icon = Icons.Outlined.VolumeUp,
                title = strings.autoSpeakerphoneTitle,
                checked = settings.autoSpeakerphone,
                onCheckedChange = { viewModel.updateAutoSpeakerphone(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SliderRow(
                icon = Icons.Outlined.Timer,
                label = strings.cancelTimerLabel,
                valueText = "${settings.countdownSeconds} ${strings.secondsUnit}",
                value = settings.countdownSeconds.toFloat(),
                range = 5f..30f,
                steps = 5,
                onChange = { viewModel.updateCountdownSeconds(it.toInt()) }
            )
        }

        // ── Шаблон SMS ───────────────────────────────────────────────
        SectionCard(icon = Icons.AutoMirrored.Outlined.Chat, title = strings.smsTemplateSectionTitle) {
            OutlinedTextField(
                value = templateInput,
                onValueChange = {
                    templateInput = it
                    viewModel.updateCustomTemplate(it)
                },
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text(strings.smsTemplateVariablesHint, fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_template_input"),
                minLines = 2,
                maxLines = 4
            )

            val liveSms = viewModel.dispatcher.buildSmsMessage(
                source = AlertSource.ESP32_FALL,
                userName = settings.userName,
                address = locationInfo.address,
                latitude = locationInfo.latitude,
                longitude = locationInfo.longitude,
                accuracyMeters = locationInfo.accuracy,
                customTemplate = templateInput,
                languageCode = settings.appLanguage
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = liveSms,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ── Надёжность тревоги ───────────────────────────────────────
        SectionCard(icon = Icons.Outlined.Shield, title = strings.reliabilityTitle) {
            ReliabilityRow(
                icon = Icons.Outlined.Notifications,
                label = strings.reliabilityNotifications,
                ok = reliability.notificationsEnabled,
                fixText = strings.reliabilityAllowButton,
                onFix = { ReliabilityHelper.openNotificationSettings(context) }
            )
            ReliabilityRow(
                icon = Icons.Outlined.Alarm,
                label = strings.reliabilityAlertChannel,
                ok = reliability.alertChannelOk,
                fixText = strings.reliabilityAllowButton,
                onFix = { ReliabilityHelper.openAlertChannelSettings(context) }
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ReliabilityRow(
                    icon = Icons.Outlined.Fullscreen,
                    label = strings.reliabilityFullScreen,
                    ok = reliability.fullScreenAllowed,
                    fixText = strings.reliabilityAllowButton,
                    onFix = { ReliabilityHelper.openFullScreenIntentSettings(context) }
                )
            }
            ReliabilityRow(
                icon = Icons.Outlined.BatteryFull,
                label = strings.reliabilityBattery,
                ok = reliability.batteryUnrestricted,
                fixText = strings.reliabilityAllowButton,
                onFix = { ReliabilityHelper.openBatterySettings(context) }
            )
            ReliabilityRow(
                icon = Icons.Outlined.DoNotDisturbOn,
                label = strings.reliabilityDnd,
                ok = false,
                optional = true,
                fixText = strings.reliabilityHowTo,
                onFix = { showDndHelp = true }
            )

            if (ReliabilityHelper.needsOemAutostartHint()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = strings.reliabilityOemHint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showWizard = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.reliabilitySetupButton, fontSize = 13.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        ReliabilityHelper.scheduleTestAlarm(context)
                        android.widget.Toast.makeText(
                            context,
                            strings.reliabilityTestHint,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.reliabilityTestButton, fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        FamilyAppSection(
            cloud = viewModel.familyCloud,
            text = com.example.i18n.FamilyText.get(settings.appLanguage)
        )

        // ── Язык ─────────────────────────────────────────────────────
        SectionCard(icon = Icons.Outlined.Language, title = strings.languageSectionTitle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = settings.appLanguage.equals(lang.code, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) BorderStroke(1.5.dp, PrimaryBlue) else null,
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (showDndHelp) {
            DndHelpDialog(
                strings = strings,
                onDismiss = { showDndHelp = false }
            )
        }

        if (showWizard) {
            ReliabilityWizardDialog(
                strings = strings,
                onDismiss = { showWizard = false },
                onTestAlarm = {
                    showWizard = false
                    ReliabilityHelper.scheduleTestAlarm(context)
                }
            )
        }
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = 34.dp)
        )
    }
}

@Composable
private fun ReliabilityRow(
    icon: ImageVector,
    label: String,
    ok: Boolean,
    fixText: String,
    optional: Boolean = false,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        when {
            ok -> Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = StatusConnected,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
            )
            else -> TextButton(onClick = onFix) {
                if (!optional) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(fixText, fontSize = 12.sp)
            }
        }
    }
}
