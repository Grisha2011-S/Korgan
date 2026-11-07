package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cloud.PhoneRole
import com.example.i18n.FamilyText
import com.example.ui.theme.PrimaryBlue

/** Первый вопрос после выбора языка: этот телефон носит браслет или следит за близким. */
@Composable
fun RoleOnboardingDialog(
    languageCode: String,
    onConfirm: (PhoneRole) -> Unit
) {
    val text = FamilyText.get(languageCode)
    var selected by remember { mutableStateOf(PhoneRole.WEARER) }

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
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = text.roleTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                RoleOption(Icons.Default.Watch, text.roleWearerTitle, text.roleWearerSubtitle,
                    selected == PhoneRole.WEARER) { selected = PhoneRole.WEARER }
                RoleOption(Icons.Default.FamilyRestroom, text.roleCaregiverTitle, text.roleCaregiverSubtitle,
                    selected == PhoneRole.CAREGIVER) { selected = PhoneRole.CAREGIVER }
                Button(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Text(text.continueButton)
                }
            }
        }
    }
}

@Composable
private fun RoleOption(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = if (selected) BorderStroke(1.5.dp, PrimaryBlue)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
