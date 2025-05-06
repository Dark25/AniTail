package com.anitail.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun JamControls(
    isJamEnabled: Boolean,
    isJamHost: Boolean,
    hostIp: String,
    onJamEnabledChange: (Boolean) -> Unit,
    onJamHostChange: (Boolean) -> Unit,
    onHostIpChange: (String) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("JAM LAN Sync", modifier = Modifier.weight(1f))
            Switch(checked = isJamEnabled, onCheckedChange = onJamEnabledChange)
        }
        if (isJamEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modo Host", modifier = Modifier.weight(1f))
                RadioButton(selected = isJamHost, onClick = { onJamHostChange(true) })
                Text("Cliente", modifier = Modifier.weight(1f))
                RadioButton(selected = !isJamHost, onClick = { onJamHostChange(false) })
            }
            if (!isJamHost) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hostIp,
                    onValueChange = onHostIpChange,
                    label = { Text("IP del host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
