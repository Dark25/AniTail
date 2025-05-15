package com.anitail.music.ui.screens.settings.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import com.anitail.music.R
import com.anitail.music.lyrics.MusixmatchLyricsProvider
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.rememberPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusixmatchSettingsScreen(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    // Preferencias
    val email by rememberPreference(
        key = stringPreferencesKey("musixmatch_email"),
        defaultValue = ""
    )
    
    val password by rememberPreference(
        key = stringPreferencesKey("musixmatch_password"),
        defaultValue = ""
    )
    
    val isAuthenticated by rememberPreference(
        key = booleanPreferencesKey("musixmatch_authenticated"),
        defaultValue = false
    )
    
    val tokenTimestampStr by rememberPreference(
        key = stringPreferencesKey("musixmatch_token_timestamp"),
        defaultValue = "0"
    )
    
    val preferredLanguage by rememberPreference(
        key = stringPreferencesKey("musixmatch_preferred_language"),
        defaultValue = "es"
    )
    
    val showTranslations by rememberPreference(
        key = booleanPreferencesKey("musixmatch_show_translations"),
        defaultValue = true
    )
    
    val maxResultsStr by rememberPreference(
        key = stringPreferencesKey("musixmatch_results_max"),
        defaultValue = "3"
    )    // Estados locales
    var emailState by remember { mutableStateOf(email) }
    var passwordState by remember { mutableStateOf(password) }
    var preferredLanguageState by remember { mutableStateOf(preferredLanguage) }
    var showTranslationsState by remember { mutableStateOf(showTranslations) }
    var maxResultsFloat by remember { mutableFloatStateOf(maxResultsStr.toFloatOrNull() ?: 3f) }
    var isTestingLogin by remember { mutableStateOf(false) }
    var lastLoginTime by remember { mutableStateOf<String?>(null) }    // Cargar los valores iniciales y datos de autenticación guardados
    LaunchedEffect(Unit) {
        // Cargar datos de UI
        emailState = email
        passwordState = password
        preferredLanguageState = preferredLanguage
        showTranslationsState = showTranslations
        maxResultsFloat = maxResultsStr.toFloatOrNull() ?: 3f
        
        // Cargar datos de autenticación guardados
        MusixmatchLyricsProvider.loadSavedAuthData(navController.context)
        
        // Obtener información de la sesión
        val timestamp = tokenTimestampStr.toLongOrNull()
        if (timestamp != null && timestamp > 0) {
            val date = java.util.Date(timestamp)
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            lastLoginTime = dateFormat.format(date)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configuración de Musixmatch",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Estado de autenticación
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Estado de la cuenta",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {                        Column {
                            Text(
                                text = if (isAuthenticated) "Autenticado" else "No autenticado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAuthenticated) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            )
                            
                            if (isAuthenticated && lastLoginTime != null) {
                                Text(
                                    text = "Última sesión: $lastLoginTime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (isAuthenticated) {
                            Row {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            // Intenta verificar que las credenciales siguen siendo válidas
                                            isTestingLogin = true
                                            MusixmatchLyricsProvider.loadSavedAuthData(navController.context)
                                            val result =                                            MusixmatchLyricsProvider.login(navController.context)
                                                .onSuccess { success ->
                                                    snackbarHostState.showSnackbar(
                                                        if (success) "Sesión renovada exitosamente" else "Sesión expirada"
                                                    )
                                                    
                                                    // Actualizar la UI con la nueva información de tiempo
                                                    if (success) {
                                                        val timestamp = navController.context.dataStore.data.first()[
                                                            stringPreferencesKey("musixmatch_token_timestamp")
                                                        ]?.toLongOrNull()
                                                        
                                                        if (timestamp != null) {
                                                            val date = java.util.Date(timestamp)
                                                            val dateFormat = java.text.SimpleDateFormat(
                                                                "dd/MM/yyyy HH:mm", 
                                                                java.util.Locale.getDefault()
                                                            )
                                                            lastLoginTime = dateFormat.format(date)
                                                        }
                                                    }
                                                }
                                                .onFailure {
                                                    snackbarHostState.showSnackbar("Error: ${it.localizedMessage}")
                                                }
                                            isTestingLogin = false
                                        }
                                    },
                                    enabled = !isTestingLogin
                                ) {
                                    Text("Renovar")
                                }
                                
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            // Cerrar sesión
                                            isTestingLogin = true
                                            MusixmatchLyricsProvider.logout(navController.context)
                                            snackbarHostState.showSnackbar("Sesión cerrada")
                                            
                                            // Actualizar la UI
                                            lastLoginTime = null
                                            isTestingLogin = false
                                        }
                                    },
                                    enabled = !isTestingLogin
                                ) {
                                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            
            // Credenciales de Musixmatch
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Credenciales de Musixmatch",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = emailState,
                        onValueChange = { emailState = it },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Email"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = passwordState,
                        onValueChange = { passwordState = it },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = "Contraseña"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = {
                            // Guardar credenciales
                            coroutineScope.launch {
                                MusixmatchLyricsProvider.setEmail(navController.context, emailState)
                                MusixmatchLyricsProvider.setPassword(navController.context, passwordState)
                                
                                // Intenta iniciar sesión
                                isTestingLogin = true
                                MusixmatchLyricsProvider.login(navController.context)
                                    .onSuccess { success ->
                                        snackbarHostState.showSnackbar(
                                            if (success) "Login exitoso" else "Login fallido"
                                        )
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar("Error: ${it.localizedMessage}")
                                    }
                                isTestingLogin = false
                            }
                        },
                        enabled = !isTestingLogin && emailState.isNotBlank() && passwordState.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (isTestingLogin) "Iniciando..." else "Iniciar sesión")
                    }
                }
            }
            
            // Configuración de traducción
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Configuración de traducciones",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = preferredLanguageState,
                        onValueChange = { preferredLanguageState = it },
                        label = { Text("Idioma preferido (código ISO, ej: es, en, fr)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Idioma"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mostrar traducciones (requiere cuenta Premium)")
                        
                        Checkbox(
                            checked = showTranslationsState,
                            onCheckedChange = { showTranslationsState = it }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                MusixmatchLyricsProvider.setPreferredLanguage(
                                    navController.context, 
                                    preferredLanguageState
                                )
                                MusixmatchLyricsProvider.setShowTranslations(
                                    navController.context, 
                                    showTranslationsState
                                )
                                
                                snackbarHostState.showSnackbar("Configuración de traducción guardada")
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar")
                    }
                }
            }
            
            // Configuración de búsqueda
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Configuración de búsqueda",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Número máximo de resultados alternativos: ${maxResultsFloat.toInt()}"
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Slider(
                        value = maxResultsFloat,
                        onValueChange = { maxResultsFloat = it },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                MusixmatchLyricsProvider.setMaxResults(
                                    navController.context, 
                                    maxResultsFloat.toInt()
                                )
                                
                                snackbarHostState.showSnackbar("Configuración de búsqueda guardada")
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar")
                    }
                }
            }
            
            // Explicación de la funcionalidad
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Acerca de las funciones de Musixmatch",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "• Las traducciones requieren una cuenta Premium de Musixmatch\n" +
                              "• La búsqueda mejorada facilita encontrar letras más precisas\n" +
                              "• Los resultados alternativos aparecen cuando hay varias coincidencias\n" +
                              "• Se recomienda usar el idioma original y el idioma de traducción con códigos ISO (es, en, fr, etc.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
