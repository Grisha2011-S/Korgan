package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloud.AlertDelivery
import com.example.cloud.CloudException
import com.example.cloud.FamilyCloud
import com.example.cloud.PhoneRole
import com.example.i18n.FamilyStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusConnected
import kotlinx.coroutines.launch

/** Настройки телефона с браслетом: сервер, связка с родственниками по коду, способ оповещения. */
@Composable
fun FamilyAppSection(cloud: FamilyCloud, text: FamilyStrings) {
    val state by cloud.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var urlInput by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var codeInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(state.registered) {
        if (state.registered) cloud.refreshLinks()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.example.ui.components.IconBadge(
                    icon = Icons.Outlined.FamilyRestroom,
                    tint = PrimaryBlue,
                    size = 36.dp
                )
                Text(text.familySectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            ServerStatusRow(state.registered, state.online, text)

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(text.serverUrlLabel) },
                supportingText = { Text(text.serverUrlHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        cloud.connect(urlInput).onFailure {
                            message = text.serverError.format(it.message ?: "") to false
                        }.onSuccess { message = null }
                        busy = false
                    }
                },
                enabled = !busy && urlInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) text.connecting else text.connectButton)
            }

            if (state.registered) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.filter(Char::isDigit).take(6) },
                        label = { Text(text.enterCodeLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                cloud.joinWithCode(codeInput)
                                    .onSuccess {
                                        message = text.linkedSuccess.format(it.name) to true
                                        codeInput = ""
                                    }
                                    .onFailure {
                                        message = when ((it as? CloudException)?.code) {
                                            404 -> text.codeWrong
                                            429 -> text.tooManyAttempts
                                            else -> text.serverError.format(it.message ?: "")
                                        } to false
                                    }
                                busy = false
                            }
                        },
                        enabled = !busy && codeInput.length == 6
                    ) { Text(text.linkButton) }
                }
            }

            message?.let { (msg, ok) ->
                Text(msg, color = if (ok) StatusConnected else EmergencyRed, style = MaterialTheme.typography.bodySmall)
            }

            if (state.registered) {
                Text(text.recipientsInApp, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (state.links.isEmpty()) {
                    Text(text.noRecipientsInApp, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.links.forEach { link ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("• " + link.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { cloud.removeLink(link.deviceId) } }) {
                            Icon(Icons.Default.Close, contentDescription = text.removeLink)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text(text.deliveryTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            listOf(
                Triple(AlertDelivery.APP_THEN_SMS, text.deliveryAppThenSms, text.deliveryAppThenSmsHint),
                Triple(AlertDelivery.APP, text.deliveryApp, text.deliveryAppHint),
                Triple(AlertDelivery.SMS, text.deliverySms, text.deliverySmsHint),
                Triple(AlertDelivery.BOTH, text.deliveryBoth, text.deliveryBothHint)
            ).forEach { (mode, title, hint) ->
                val selected = state.delivery == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = if (selected) BorderStroke(1.5.dp, PrimaryBlue) else null,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().clickable { cloud.updateDelivery(mode) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        RadioButton(selected = selected, onClick = { cloud.updateDelivery(mode) })
                        Column {
                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(hint, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.delivery == AlertDelivery.APP_THEN_SMS) {
                Column {
                    Text(text.ackTimeoutLabel.format(state.ackTimeoutSeconds), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.ackTimeoutSeconds.toFloat(),
                        onValueChange = { cloud.updateAckTimeout((it / 30f).toInt().coerceIn(1, 10) * 30) },
                        valueRange = 30f..300f,
                        steps = 8
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            TextButton(onClick = { cloud.setRole(PhoneRole.UNSET) }) { Text(text.changeRole) }
        }
    }
}

@Composable
fun ServerStatusRow(registered: Boolean, online: Boolean?, text: FamilyStrings) {
    val ok = registered && online != false
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = if (ok) Icons.Default.Cloud else Icons.Default.CloudOff,
            contentDescription = null,
            tint = if (ok) StatusConnected else EmergencyRed,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = when {
                !registered -> text.notConnected
                online == false -> text.serverOffline
                else -> text.serverOnline
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) StatusConnected else EmergencyRed
        )
    }
}
