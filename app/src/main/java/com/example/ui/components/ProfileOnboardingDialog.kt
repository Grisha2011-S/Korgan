package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.EmergencyContact
import com.example.data.isValidPhoneNumber
import com.example.i18n.AppStrings
import com.example.ui.theme.PrimaryBlue

@Composable
fun ProfileOnboardingDialog(
    initialName: String,
    initialContacts: List<EmergencyContact>,
    languageCode: String,
    onConfirm: (name: String, contacts: List<EmergencyContact>) -> Unit
) {
    val strings = AppStrings.get(languageCode)
    var name by remember { mutableStateOf(initialName) }
    var contacts by remember {
        mutableStateOf(initialContacts.ifEmpty { listOf(EmergencyContact()) })
    }
    var showErrors by remember { mutableStateOf(false) }

    val cleaned = contacts.map { it.copy(name = it.name.trim(), phone = it.phone.trim()) }
    val validCount = cleaned.count { isValidPhoneNumber(it.phone) }
    val hasInvalidPhone = cleaned.any {
        it.phone.isNotBlank() && !isValidPhoneNumber(it.phone)
    }
    val nameIsValid = name.trim().length >= 2
    val canSave = nameIsValid && validCount > 0 && !hasInvalidPhone

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.profileSetupTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.profileSetupSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.profileSetupNameLabel) },
                    placeholder = { Text(strings.profileSetupNamePlaceholder) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    isError = showErrors && !nameIsValid,
                    supportingText = if (showErrors && !nameIsValid) {
                        { Text(strings.profileSetupErrorName) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.profileSetupContactsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.profileSetupContactsHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { contacts = contacts + EmergencyContact() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("profile_add_contact")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.profileSetupAddContact, fontSize = 12.sp)
                    }
                }

                contacts.forEachIndexed { index, contact ->
                    key(contact.id) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = strings.contactNumberLabel.format(index + 1),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (index == 0) {
                                            Text(
                                                text = strings.primaryContactBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryBlue
                                            )
                                        }
                                    }
                                    if (contacts.size > 1) {
                                        IconButton(
                                            onClick = {
                                                contacts = contacts.filterNot { it.id == contact.id }
                                            },
                                            modifier = Modifier.testTag("profile_remove_contact_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = strings.removeContact,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = contact.name,
                                    onValueChange = { newName ->
                                        contacts = contacts.map {
                                            if (it.id == contact.id) it.copy(name = newName) else it
                                        }
                                    },
                                    label = { Text(strings.contactNameLabel) },
                                    placeholder = { Text(strings.contactNamePlaceholder) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_contact_name_$index")
                                )

                                val phoneInvalid = contact.phone.isNotBlank() &&
                                    !isValidPhoneNumber(contact.phone)
                                OutlinedTextField(
                                    value = contact.phone,
                                    onValueChange = { newPhone ->
                                        contacts = contacts.map {
                                            if (it.id == contact.id) it.copy(phone = newPhone) else it
                                        }
                                    },
                                    label = { Text(strings.contactPhoneLabel) },
                                    placeholder = { Text(strings.contactPhonePlaceholder) },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    isError = showErrors && phoneInvalid,
                                    supportingText = if (showErrors && phoneInvalid) {
                                        { Text(strings.profileSetupErrorPhone) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_contact_phone_$index")
                                )
                            }
                        }
                    }
                }

                if (showErrors && (validCount == 0 || hasInvalidPhone)) {
                    Text(
                        text = strings.profileSetupErrorAtLeastOne,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = strings.profileSetupHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = {
                        if (canSave) {
                            val prepared = contacts
                                .map { it.copy(name = it.name.trim(), phone = it.phone.trim()) }
                                .filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                            onConfirm(name.trim(), prepared)
                        } else {
                            showErrors = true
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("profile_save_button")
                ) {
                    Text(
                        text = strings.profileSetupSave,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
