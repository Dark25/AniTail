package com.anitail.music.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.anitail.music.R
import com.anitail.music.ui.components.CircularProgressIndicator
import com.anitail.music.utils.LanJamServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamSettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val jamViewModel: JamViewModel = viewModel()
    val isJamEnabled = jamViewModel.isJamEnabled.collectAsState().value
    val isJamHost = jamViewModel.isJamHost.collectAsState().value
    val hostIp = jamViewModel.hostIp.collectAsState().value
    val availableHosts = jamViewModel.availableHosts.collectAsState().value
      // Obtener y establecer la IP local cuando esté en modo host
    LaunchedEffect(isJamHost, isJamEnabled) {
        if (isJamHost && isJamEnabled) {
            // Crear un servidor temporal solo para obtener la IP
            val tempServer = LanJamServer(
                onMessage = { /* No hacer nada, solo nos interesa la IP */ },
                onClientConnected = { _, _ -> /* No hacer nada */ }
            )
            val localIp = tempServer.getLocalIpAddress()
            jamViewModel.updateLocalIpAddress(localIp)
        } else        if (!isJamHost && isJamEnabled) {
            // Si cambiamos a modo cliente, iniciar un escaneo de hosts solo si no hay hosts descubiertos
            if (jamViewModel.availableHosts.value.isEmpty()) {
                jamViewModel.scanForHosts()
            }
        }
    }
    
    // Animaciones para el indicador de estado
    val connectionProgress by animateFloatAsState(
        targetValue = if (isJamEnabled) 1f else 0f,
        label = "connection_progress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.jam_lan_sync))
                        Text(
                            text = stringResource(R.string.jam_settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Estado de conexión
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Indicador de estado circular
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Círculo de fondo
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            
                            // Icono de música
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = if (isJamEnabled) 
                                    MaterialTheme.colorScheme.primary
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            
                            // Anillo de progreso
                            CircularProgressIndicator(
                                progress = connectionProgress,
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(R.string.jam_music_in_sync),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isJamEnabled) 
                                stringResource(R.string.jam_online) 
                            else 
                                stringResource(R.string.jam_offline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isJamEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Controles principales de JAM
            JamControls(
                isJamEnabled = isJamEnabled,
                isJamHost = isJamHost,
                hostIp = hostIp,
                onJamEnabledChange = { jamViewModel.setJamEnabled(it) },
                onJamHostChange = { jamViewModel.setJamHost(it) },
                onHostIpChange = { jamViewModel.setHostIp(it) },
                viewModel = jamViewModel
            )
        }
    }
}
