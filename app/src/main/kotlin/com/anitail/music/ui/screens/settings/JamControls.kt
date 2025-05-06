package com.anitail.music.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anitail.music.R

@Composable
fun JamControls(
    isJamEnabled: Boolean,
    isJamHost: Boolean,
    hostIp: String,
    onJamEnabledChange: (Boolean) -> Unit,
    onJamHostChange: (Boolean) -> Unit,
    onHostIpChange: (String) -> Unit,
    viewModel: JamViewModel
) {
    val connectionHistory by viewModel.connectionHistory.collectAsState()
    val activeConnections by viewModel.activeConnections.collectAsState()
    var showConnectionHistory by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.jam_lan_sync),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.jam_enable_lan_sync),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isJamEnabled, onCheckedChange = onJamEnabledChange)
            }
            
            AnimatedVisibility(
                visible = isJamEnabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.jam_connection_mode),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isJamHost, 
                                    onClick = { onJamHostChange(true) }
                                )
                                Text(stringResource(R.string.jam_host_mode))
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = !isJamHost, 
                                    onClick = { onJamHostChange(false) }
                                )
                                Text(stringResource(R.string.jam_client_mode))
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    if (!isJamHost) {
                        OutlinedTextField(
                            value = hostIp,
                            onValueChange = onHostIpChange,
                            label = { Text(stringResource(R.string.jam_host_ip)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Si es host, mostrar botón para ver conexiones
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.jam_clients_connect_info),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Button(
                                onClick = { showConnectionHistory = !showConnectionHistory },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (showConnectionHistory)
                                        stringResource(R.string.jam_hide_connection_history)
                                    else
                                        stringResource(R.string.jam_show_connection_history)
                                )
                            }
                            
                            AnimatedVisibility(
                                visible = showConnectionHistory,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    
                                    // Mostrar conexiones activas si hay alguna
                                    if (activeConnections.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.jam_active_connections),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .padding(vertical = 8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(8.dp)
                                        ) {
                                            items(activeConnections) { connection ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = connection.ip,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = connection.connectedAt,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            thickness = DividerDefaults.Thickness,
                                            color = DividerDefaults.color
                                        )
                                    }
                                    
                                    // Historial de conexiones
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.jam_connection_history),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        TextButton(onClick = { viewModel.clearConnectionHistory() }) {
                                            Text(stringResource(R.string.clear))
                                        }
                                    }
                                    
                                    if (connectionHistory.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                stringResource(R.string.jam_no_connections),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .padding(vertical = 8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(8.dp)
                                        ) {
                                            items(connectionHistory) { connection ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = connection.ip,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = connection.connectedAt,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                
                                                if (connectionHistory.indexOf(connection) < connectionHistory.size - 1) {
                                                    HorizontalDivider(
                                                        modifier = Modifier
                                                            .padding(vertical = 2.dp)
                                                            .fillMaxWidth(0.9f),
                                                        thickness = DividerDefaults.Thickness,
                                                        color = DividerDefaults.color
                                                    )
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
        }
    }
}
