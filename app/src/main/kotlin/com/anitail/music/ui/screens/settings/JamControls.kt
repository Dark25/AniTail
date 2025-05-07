package com.anitail.music.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    
    // Tarjeta principal activar/desactivar
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = if (isJamEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp)
            )
            
            Text(
                stringResource(R.string.jam_enable_lan_sync),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            Switch(
                checked = isJamEnabled, 
                onCheckedChange = onJamEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
    
    // Modos de conexión
    AnimatedVisibility(
        visible = isJamEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column {
            // Tarjetas de modo
            Text(
                text = stringResource(R.string.jam_connection_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Host card
                ConnectionModeCard(
                    title = stringResource(R.string.jam_host_mode),
                    description = stringResource(R.string.jam_host_description),
                    icon = Icons.Default.Router,
                    isSelected = isJamHost,
                    onClick = { onJamHostChange(true) },
                    modifier = Modifier.weight(1f)
                )
                
                // Client card
                ConnectionModeCard(
                    title = stringResource(R.string.jam_client_mode),
                    description = stringResource(R.string.jam_client_description),
                    icon = Icons.Outlined.Computer,
                    isSelected = !isJamHost,
                    onClick = { onJamHostChange(false) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Detalles específicos según el modo
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (isJamHost) {
                    HostModeContent(
                        viewModel = viewModel,
                        activeConnections = activeConnections,
                        connectionHistory = connectionHistory,
                        showConnectionHistory = showConnectionHistory,
                        onToggleHistory = { showConnectionHistory = !showConnectionHistory }
                    )
                } else {
                    ClientModeContent(
                        hostIp = hostIp,
                        onHostIpChange = onHostIpChange
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 6.dp else 1.dp,
        label = "card_elevation",
        animationSpec = tween(durationMillis = 300)
    )

    Card(
        modifier = modifier
            .shadow(elevation = elevation)
            .clickable(onClick = onClick)
            .padding(4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = if (isSelected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Icono del modo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) 
                        MaterialTheme.colorScheme.primary
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Título
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Descripción
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun HostModeContent(
    viewModel: JamViewModel,
    activeConnections: List<JamViewModel.JamConnection>,
    connectionHistory: List<JamViewModel.JamConnection>,
    showConnectionHistory: Boolean,
    onToggleHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Server status card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.jam_server_status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.jam_online),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Server IP address
                Text(
                    text = stringResource(R.string.jam_server_address),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            // This would be the real IP from LanJamServer.getLocalIpAddress()
                            text = "192.168.1.X",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        FilledTonalButton(
                            onClick = { /* Copy to clipboard */ },
                            modifier = Modifier.padding(start = 8.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = "Compartir")
                        }
                    }
                }
            }
        }
        
        // Connected devices
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.outlinedCardColors()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                    
                    Text(
                        text = stringResource(R.string.jam_connected_devices),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedButton(
                        onClick = onToggleHistory,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            text = if (showConnectionHistory)
                                stringResource(R.string.jam_hide_connection_history)
                            else
                                stringResource(R.string.jam_show_connection_history),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Active connections
                if (activeConnections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.jam_no_connections),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(activeConnections) { connection ->
                            ConnectionItem(
                                ipAddress = connection.ip,
                                timestamp = connection.connectedAt,
                                isActive = true
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = showConnectionHistory && connectionHistory.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.jam_connection_history),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            
                            FilledIconButton(
                                onClick = { viewModel.clearConnectionHistory() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            items(connectionHistory) { connection ->
                                ConnectionItem(
                                    ipAddress = connection.ip,
                                    timestamp = connection.connectedAt,
                                    isActive = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionItem(
    ipAddress: String,
    timestamp: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicador de estado
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
        )
        
        Spacer(modifier = Modifier.size(12.dp))
        
        // IP Address
        Text(
            text = ipAddress,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        
        // Timestamp
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ClientModeContent(
    hostIp: String,
    onHostIpChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Server connection card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.jam_server_address),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = hostIp,
                    onValueChange = onHostIpChange,
                    label = { Text(stringResource(R.string.jam_host_ip)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { /* Scan network for servers */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.jam_scan_network))
                }
            }
        }
    }
}

