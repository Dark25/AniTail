package com.anitail.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.anitail.music.App
import com.anitail.music.constants.EnableMusixmatchKey
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.utils.reportException
import com.maxrave.lyricsproviders.LyricsProviders
import com.maxrave.lyricsproviders.models.response.MusixmatchCredential
import com.maxrave.lyricsproviders.models.response.MusixmatchTranslationLyricsResponse
import com.maxrave.lyricsproviders.models.response.SearchMusixmatchResponse
import com.maxrave.lyricsproviders.parser.MusixmatchLyricsParser
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

object MusixmatchLyricsProvider : LyricsProvider {
    override val name: String = "Musixmatch"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMusixmatchKey] ?: true

    // Nuevas claves de preferencias para Musixmatch
    private val MUSIXMATCH_EMAIL = stringPreferencesKey("musixmatch_email")
    private val MUSIXMATCH_PASSWORD = stringPreferencesKey("musixmatch_password")
    private val MUSIXMATCH_AUTHENTICATED = booleanPreferencesKey("musixmatch_authenticated")
    private val MUSIXMATCH_PREFERRED_LANGUAGE = stringPreferencesKey("musixmatch_preferred_language")
    private val MUSIXMATCH_SHOW_TRANSLATIONS = booleanPreferencesKey("musixmatch_show_translations")
    private val MUSIXMATCH_RESULTS_MAX = stringPreferencesKey("musixmatch_results_max")
    private val MUSIXMATCH_USER_TOKEN = stringPreferencesKey("musixmatch_user_token")
    private val MUSIXMATCH_COOKIE = stringPreferencesKey("musixmatch_cookie")
    private val MUSIXMATCH_TOKEN_TIMESTAMP = stringPreferencesKey("musixmatch_token_timestamp")
    private var lastTokenFetchTimestamp = 0L
    private var userToken: String? = null
    private var isUserAuthenticated = false
    
    // Track if we've attempted initial loading
    @Volatile
    private var hasAttemptedInitialLoad = false
    
    // Track captcha requests to implement rate limiting
    private var lastCaptchaTimestamp = 0L
    private var captchaErrorCount = 0
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    private val lyricsProviders by lazy {
        LyricsProviders(App.instance, json)
    }
    
    /**
     * Carga los datos de autenticación guardados en DataStore
     * Returns true if authentication was successfully restored from saved data
     */
    suspend fun loadSavedAuthData(context: Context): Boolean {
        try {
            // Track that we've attempted to load saved data
            hasAttemptedInitialLoad = true
            
            val savedToken = context.dataStore.data.first()[MUSIXMATCH_USER_TOKEN]
            val savedCookie = context.dataStore.data.first()[MUSIXMATCH_COOKIE]
            val savedTimestamp = context.dataStore.data.first()[MUSIXMATCH_TOKEN_TIMESTAMP]?.toLongOrNull() ?: 0L
            val isAuthenticated = context.dataStore.data.first()[MUSIXMATCH_AUTHENTICATED] ?: false
            
            if (!savedToken.isNullOrEmpty() && !savedCookie.isNullOrEmpty() && isAuthenticated) {
                userToken = savedToken
                lyricsProviders.musixmatchUserToken = savedToken
                lyricsProviders.musixmatchCookie = savedCookie
                lastTokenFetchTimestamp = savedTimestamp
                isUserAuthenticated = true
                Timber.d("Musixmatch credentials loaded from DataStore")
                
                // If the token is expired, try to refresh it immediately
                if (System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) { // 5 hours instead of 6 to preemptively refresh
                    Timber.d("Musixmatch token expired, refreshing")
                    // Try to refresh the token but don't fail if it doesn't work
                    try {
                        refreshToken(context)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to refresh Musixmatch token during initialization, will retry later")
                        // We'll still return true since we have a token, even if expired
                    }
                }
                
                return true
            } else {
                // Check if we have email/password credentials to try authentication
                val email = context.dataStore.data.first()[MUSIXMATCH_EMAIL]
                val password = context.dataStore.data.first()[MUSIXMATCH_PASSWORD]
                
                if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    Timber.d("Attempting Musixmatch login with saved credentials")
                    return login(context).getOrDefault(false)
                }
            }
            
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error loading Musixmatch authentication data")
            return false
        }
    }
    
    /**
     * Ensures authentication has been attempted at least once
     * Call this before any operation that requires auth
     */
    private suspend fun ensureInitialized(context: Context): Boolean {
        if (!hasAttemptedInitialLoad) {
            return loadSavedAuthData(context)
        }
        return isUserAuthenticated
    }
      /**
     * Refreshes the token if needed
     */
    private suspend fun refreshToken(context: Context): Boolean {
        // Check if we've recently encountered CAPTCHA errors and if we should rate limit our requests
        if (checkCaptchaRateLimit()) {
            Timber.w("Musixmatch token refresh skipped due to recent CAPTCHA challenges")
            return false
        }
        
        try {
            val tokenResponse = lyricsProviders.getMusixmatchUserToken()
            
            // Check for CAPTCHA challenge in the response
            val responseText = tokenResponse.bodyAsText()
            if (responseText.contains("\"hint\":\"captcha\"")) {
                Timber.w("Musixmatch is requesting CAPTCHA verification")
                handleCaptchaDetected()
                return false
            }
            
            val newToken = MusixmatchLyricsParser.getToken(tokenResponse)
            
            if (newToken.isEmpty()) {
                Timber.e("Failed to extract Musixmatch token from response")
                return false
            }
            
            userToken = newToken
            lyricsProviders.musixmatchUserToken = newToken
            lastTokenFetchTimestamp = System.currentTimeMillis()
            
            // Save to DataStore
            context.dataStore.edit { preferences ->
                preferences[MUSIXMATCH_USER_TOKEN] = newToken
                preferences[MUSIXMATCH_TOKEN_TIMESTAMP] = lastTokenFetchTimestamp.toString()
            }
            
            // Reset CAPTCHA counter on successful token refresh
            resetCaptchaCounter()
            
            Timber.d("Musixmatch token refreshed: $newToken")
            return true
        } catch (e: Exception) {
            // Check if this is a CAPTCHA or parsing error
            if (e.message?.contains("Expected start of the object '{', but had '['") == true || 
                e.stackTraceToString().contains("hint\":\"captcha")) {
                Timber.w("CAPTCHA challenge detected from error: ${e.message}")
                handleCaptchaDetected()
            } else {
                Timber.e(e, "Error refreshing Musixmatch token")
            }
            return false
        }
    }
    
    /**
     * Tracks CAPTCHA detection and implements rate limiting
     */
    private fun handleCaptchaDetected() {
        captchaErrorCount++
        lastCaptchaTimestamp = System.currentTimeMillis()
    }
    
    /**
     * Reset CAPTCHA counter when we successfully get responses
     */
    private fun resetCaptchaCounter() {
        captchaErrorCount = 0
    }
    
    /**
     * Check if we should rate limit requests due to CAPTCHA challenges
     * Returns true if we should skip the request
     */
    private fun checkCaptchaRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        
        // If we've had multiple CAPTCHA errors, implement exponential backoff
        return when {
            captchaErrorCount == 0 -> false
            captchaErrorCount == 1 -> now - lastCaptchaTimestamp < 10_000 // 10 seconds after first CAPTCHA
            captchaErrorCount == 2 -> now - lastCaptchaTimestamp < 60_000 // 1 minute after second CAPTCHA
            captchaErrorCount == 3 -> now - lastCaptchaTimestamp < 300_000 // 5 minutes after third CAPTCHA
            else -> now - lastCaptchaTimestamp < 1800_000 // 30 minutes after more than 3 CAPTCHAs
        }
    }
    
    /**
     * Establece el email para la autenticación con Musixmatch
     */
    suspend fun setEmail(context: Context, email: String) {
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_EMAIL] = email
        }
    }
    
    /**
     * Establece la contraseña para la autenticación con Musixmatch
     */
    suspend fun setPassword(context: Context, password: String) {
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_PASSWORD] = password
        }
    }
    
    /**
     * Establece el idioma preferido para las traducciones
     */
    suspend fun setPreferredLanguage(context: Context, language: String) {
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_PREFERRED_LANGUAGE] = language
        }
    }
    
    /**
     * Establece si se deben mostrar traducciones
     */
    suspend fun setShowTranslations(context: Context, show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_SHOW_TRANSLATIONS] = show
        }
    }
    
    /**
     * Establece el número máximo de resultados a mostrar
     */
    suspend fun setMaxResults(context: Context, max: Int) {
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_RESULTS_MAX] = max.toString()
        }
    }      /**
     * Inicia sesión con la cuenta de Musixmatch
     */    suspend fun login(context: Context): Result<Boolean> = runCatching {
        val email = context.dataStore.data.first()[MUSIXMATCH_EMAIL]
        val password = context.dataStore.data.first()[MUSIXMATCH_PASSWORD]
        
        if (email == null || password == null) {
            return@runCatching false
        }
        
        // Always ensure we're initialized and have a token
        hasAttemptedInitialLoad = true
        
        // Refresh token if needed or missing
        if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) {
            if (!refreshToken(context)) {
                Timber.e("Could not refresh token before login, aborting login attempt")
                return@runCatching false
            }
        }
        
        // Intenta iniciar sesión con las credenciales
        val response = lyricsProviders.postMusixmatchPostCredentials(email, password, userToken!!)
        val credential = response.body<MusixmatchCredential>()
        Timber.d("Musixmatch login response: $credential")
        
        // Verifica si la autenticación fue exitosa
        // Si el status_code es 401 o el body está vacío, significa que la autenticación falló
        val isSuccess = if (credential.message.header.status_code == 401 || credential.message.body.isEmpty()) {
            false
        } else {
            credential.message.body.isNotEmpty() &&
            credential.message.body.getOrNull(0)?.credential?.error == null &&
            credential.message.body.getOrNull(0)?.credential?.account != null
        }
        
        // Obtener la cookie de la respuesta
        val cookie = lyricsProviders.musixmatchCookie
        
        // Actualiza el estado de autenticación y guarda token + cookie en DataStore
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_AUTHENTICATED] = isSuccess
            if (isSuccess) {
                userToken?.let { preferences[MUSIXMATCH_USER_TOKEN] = it }
                cookie?.let { preferences[MUSIXMATCH_COOKIE] = it }
                preferences[MUSIXMATCH_TOKEN_TIMESTAMP] = lastTokenFetchTimestamp.toString()
            }
        }
        
        isUserAuthenticated = isSuccess
        Timber.d("Musixmatch login ${if (isSuccess) "successful" else "failed"}")
        return@runCatching isSuccess
    }
    
    /**
     * Cierra la sesión y limpia los datos de autenticación almacenados
     */
    suspend fun logout(context: Context) {
        // Limpiar las variables en memoria
        userToken = null
        isUserAuthenticated = false
        lastTokenFetchTimestamp = 0L
        
        // Limpiar los datos en el cliente de Musixmatch
        lyricsProviders.musixmatchUserToken = null
        lyricsProviders.musixmatchCookie = null
        
        // Eliminar los datos de autenticación del DataStore pero mantener las credenciales
        context.dataStore.edit { preferences ->
            preferences[MUSIXMATCH_AUTHENTICATED] = false
            preferences.remove(MUSIXMATCH_USER_TOKEN)
            preferences.remove(MUSIXMATCH_COOKIE)
            preferences.remove(MUSIXMATCH_TOKEN_TIMESTAMP)
        }
        
        Timber.d("Sesión de Musixmatch cerrada y datos limpiados")
    }
      /**
     * Verifica si el usuario está autenticado
     */    private suspend fun checkAuthentication(context: Context): Boolean {
        // Si ya sabemos que está autenticado, no es necesario comprobar
        if (isUserAuthenticated) {
            // Even if authenticated, check token expiration and refresh if needed
            if (System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) { // 5 hours for preemptive refresh
                Timber.d("Token expirado, intentando renovarlo en segundo plano")
                refreshToken(context)
                // Continue with existing token even if refresh fails - we'll try again next time
            }
            return true
        }
        
        // Ensure we've tried to load credentials at least once
        if (!hasAttemptedInitialLoad) {
            return loadSavedAuthData(context)
        }
        
        // Comprueba si los datos de autenticación están guardados
        val savedToken = context.dataStore.data.first()[MUSIXMATCH_USER_TOKEN]
        val savedCookie = context.dataStore.data.first()[MUSIXMATCH_COOKIE]
        val authenticated = context.dataStore.data.first()[MUSIXMATCH_AUTHENTICATED] ?: false
        
        // Si tenemos token y cookie guardados y estaba autenticado, cargamos esos datos
        if (!savedToken.isNullOrEmpty() && !savedCookie.isNullOrEmpty() && authenticated) {
            userToken = savedToken
            lyricsProviders.musixmatchUserToken = savedToken
            lyricsProviders.musixmatchCookie = savedCookie
            isUserAuthenticated = true
            
            // Obtener el timestamp del token
            val savedTimestamp = context.dataStore.data.first()[MUSIXMATCH_TOKEN_TIMESTAMP]?.toLongOrNull() ?: 0L
            lastTokenFetchTimestamp = savedTimestamp
            
            // Verificar si el token ha expirado (más de 6 horas)
            if (System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) {
                Timber.d("Token expirado, intentando renovarlo")
                return login(context).getOrDefault(false)
            }
            
            return true
        }
        
        // Si no hay datos guardados, comprueba si hay credenciales para intentar login
        val email = context.dataStore.data.first()[MUSIXMATCH_EMAIL]
        val password = context.dataStore.data.first()[MUSIXMATCH_PASSWORD]
        
        // Si no tenemos credenciales, devuelve false
        if (email == null || password == null) {
            return false
        }
        
        // Intenta autenticarse con las credenciales almacenadas
        return login(context).getOrDefault(false)
    }    /**
     * Obtiene la traducción de las letras en el idioma preferido
     */
    private suspend fun getTranslation(context: Context, trackId: String): String? {
        // Check authentication and initialize if needed
        if (!isUserAuthenticated) {
            ensureInitialized(context)
            if (!checkAuthentication(context)) {
                Timber.d("No se pudo autenticar para obtener traducción")
                return null
            }
        }
        
        val preferredLanguage = context.dataStore.data.first()[MUSIXMATCH_PREFERRED_LANGUAGE] ?: "es"
        val showTranslations = context.dataStore.data.first()[MUSIXMATCH_SHOW_TRANSLATIONS] ?: false
        
        if (!showTranslations) {
            return null
        }
        
        return try {
            // Make sure we have a token
            if (userToken == null) {
                if (!refreshToken(context)) {
                    Timber.e("No token available for translation request")
                    return null
                }
            }
            
            val translationResponse = lyricsProviders.getMusixmatchTranslateLyrics(
                trackId = trackId,
                userToken = userToken ?: return null,
                language = preferredLanguage
            )
            
            // Guardar la cookie actualizada si existe
            lyricsProviders.musixmatchCookie?.let { cookie ->
                context.dataStore.edit { preferences ->
                    preferences[MUSIXMATCH_COOKIE] = cookie
                    preferences[MUSIXMATCH_AUTHENTICATED] = true
                }
                isUserAuthenticated = true
            }
            
            // Extraer la traducción de la respuesta
            val translation = translationResponse.body<MusixmatchTranslationLyricsResponse>()
            val translationData = translation.message.body.translations_list
            
            if (translationData.isNotEmpty()) {
                val translatedLines = translationData.joinToString("\n") { 
                    "${it.translation.matched_line}: ${it.translation.snippet}" 
                }
                
                return translatedLines
            }
            
            null
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener traducción de letras")
            null
        }
    }
    
    /**
     * Obtiene múltiples resultados de búsqueda para que el usuario pueda elegir
     */    suspend fun getMultipleSearchResults(context: Context, title: String, artist: String): Result<List<SearchMusixmatchResponse.Message.Body.Track.TrackX>> = runCatching {
        withContext(Dispatchers.IO) {
            // Always ensure we're properly initialized - this handles app restarts
            ensureInitialized(context)
            
            // Check authentication - this handles cases where tokens were loaded but authentication status is needed
            if (!isUserAuthenticated) {
                if (!checkAuthentication(context)) {
                    throw IllegalStateException("No se pudo autenticar con Musixmatch. Se requiere iniciar sesión.")
                }
            }
            
            // Refrescar el token si han pasado más de 5 horas desde la última obtención o es nulo
            if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) {
                if (!refreshToken(context)) {
                    // If refresh fails but we still have an old token, continue with it
                    if (userToken == null) {
                        throw IllegalStateException("No se pudo obtener o refrescar el token de Musixmatch")
                    }
                    // Otherwise we'll continue with the existing token
                    Timber.w("Token refresh failed, continuing with existing token")
                }
            }            // Check if we've encountered too many CAPTCHA errors
            if (checkCaptchaRateLimit()) {
                Timber.w("Skipping Musixmatch search due to CAPTCHA rate limiting")
                throw IllegalStateException("Musixmatch no está disponible temporalmente debido a protección contra bots. Por favor, inténtelo de nuevo más tarde.")
            }

            // Buscar la canción utilizando el título y artista
            val searchResponse = lyricsProviders.searchMusixmatchTrackId(
                q = "$artist $title",
                userToken = userToken ?: throw IllegalStateException("No se pudo obtener el token de Musixmatch")
            )
            
            // Check for CAPTCHA in the response
            val responseText = searchResponse.bodyAsText()
            if (responseText.contains("\"hint\":\"captcha\"")) {
                Timber.w("Musixmatch is requesting CAPTCHA verification during search")
                handleCaptchaDetected()
                throw IllegalStateException("Musixmatch requiere verificación CAPTCHA. Por favor, inténtelo de nuevo más tarde.")
            }

            // Aquí extraeríamos la lista de resultados múltiples
            // Nota: Esto es un ejemplo, la estructura real dependerá de la respuesta de la API
            val results = mutableListOf<SearchMusixmatchResponse.Message.Body.Track.TrackX>()
            
            try {
                val jsonResponse = searchResponse.body<SearchMusixmatchResponse>()
                
                // Reset CAPTCHA counter on successful parsing
                resetCaptchaCounter()
                
                // Procesamos los resultados basados en la estructura definida en SearchMusixmatchResponse
                val trackList = jsonResponse.message.body.track_list
                
                if (trackList != null && trackList.isNotEmpty()) {
                    trackList.forEach { track ->
                        results.add(track.track)
                    }
                }
                
                // Como alternativa, también revisamos si hay resultados en macro_result_list
                jsonResponse.message.body.macro_result_list?.track_list?.let { tracks ->
                    tracks.forEach { track ->
                        results.add(track.track)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al procesar resultados múltiples")
            }
            
            results
        }
    }
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            try {
                // Always ensure we're properly initialized - this handles app restarts
                ensureInitialized(App.instance)
                
                // Check authentication - this handles cases where tokens were loaded but authentication status is needed
                if (!isUserAuthenticated) {
                    if (!checkAuthentication(App.instance)) {
                        throw IllegalStateException("No se pudo autenticar con Musixmatch. Se requiere iniciar sesión.")
                    }
                }
                
                // Refrescar el token si han pasado más de 5 horas desde la última obtención o es nulo
                if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 5 * 60 * 60 * 1000) {
                    if (!refreshToken(App.instance)) {
                        // If refresh fails but we still have an old token, continue with it
                        if (userToken == null) {
                            throw IllegalStateException("No se pudo obtener o refrescar el token de Musixmatch")
                        }
                        // Otherwise we'll continue with the existing token
                        Timber.w("Token refresh failed, continuing with existing token")
                    }
                }                // Check if we've encountered too many CAPTCHA errors recently
                if (checkCaptchaRateLimit()) {
                    Timber.w("Skipping Musixmatch lyrics request due to CAPTCHA rate limiting")
                    throw IllegalStateException("Musixmatch no está disponible temporalmente debido a protección contra bots. Por favor, inténtelo de nuevo más tarde.")
                }

                // Buscar la canción utilizando el título y artista
                val searchResponse = lyricsProviders.searchMusixmatchTrackId(
                    q = "$artist $title",
                    userToken = userToken ?: throw IllegalStateException("No se pudo obtener el token de Musixmatch")
                )

                // Check for CAPTCHA in response before trying to parse
                val responseText = searchResponse.bodyAsText()
                if (responseText.contains("\"hint\":\"captcha\"")) {
                    Timber.w("Musixmatch is requesting CAPTCHA verification during lyrics search")
                    handleCaptchaDetected()
                    throw IllegalStateException("Musixmatch requiere verificación CAPTCHA. Por favor, inténtelo de nuevo más tarde.")
                }

                // Extraer el ID de la pista de la respuesta de búsqueda
                val trackId = MusixmatchLyricsParser.getFirstTrackId(searchResponse)
                
                // Reset CAPTCHA counter on successful search
                resetCaptchaCounter()
                
                if (trackId != null) {                    // Obtener letras para el track específico
                    val lyricsResponse = lyricsProviders.getMusixmatchLyrics(trackId, userToken!!)
                    val lyrics = MusixmatchLyricsParser.parseLyrics(lyricsResponse)
                    
                    // Guardar la cookie actualizada si existe
                    lyricsProviders.musixmatchCookie?.let { cookie ->
                        App.instance.dataStore.edit { preferences ->
                            preferences[MUSIXMATCH_COOKIE] = cookie
                            preferences[MUSIXMATCH_AUTHENTICATED] = true
                        }
                        isUserAuthenticated = true
                    }
                    
                    if (lyrics.isNotBlank()) {
                        // Si hay traducción disponible y el usuario la ha habilitado, añadirla
                        val translation = getTranslation(App.instance, trackId)
                        if (translation != null) {
                            // Combinar letras originales con traducción
                            return@withContext "$lyrics\n\n--- TRADUCCIÓN ---\n\n$translation"
                        }
                        
                        return@withContext lyrics
                    }
                }
                
                // Si no se encuentra el ID o las letras están vacías, probamos con getMusixmatchLyricsByQ
                // Este método es útil cuando la búsqueda normal no encuentra resultados
                val durationString = (duration / 1000).toString()
                val fixResponse = lyricsProviders.fixSearchMusixmatch(
                    q_artist = artist,
                    q_track = title,
                    q_duration = durationString,
                    userToken = userToken ?: throw IllegalStateException("No se pudo obtener el token de Musixmatch")
                )
                
                // Intentar extraer un TrackX de la respuesta para usar getMusixmatchLyricsByQ
                val jsonResponse = fixResponse.body<SearchMusixmatchResponse>()
                
                // Procesamos los resultados directamente de track_list en la respuesta principal
                val track = jsonResponse.message.body.track_list?.firstOrNull()?.track
                
                if (track != null) {
                    // Usar el método alternativo de búsqueda directa por Q
                    val lyricsByQResponse = lyricsProviders.getMusixmatchLyricsByQ(track, userToken!!)
                    val lyricsByQ = MusixmatchLyricsParser.parseLyrics(lyricsByQResponse)
                    
                    if (lyricsByQ.isNotBlank()) {
                        // Si hay traducción disponible, añadirla
                        val translation = getTranslation(App.instance, track.track_id.toString())
                        if (translation != null) {
                            return@withContext "$lyricsByQ\n\n--- TRADUCCIÓN ---\n\n$translation"
                        }
                        
                        return@withContext lyricsByQ
                    }
                }
                
                // También verificamos si hay resultados en macro_result_list
                jsonResponse.message.body.macro_result_list?.track_list?.firstOrNull()?.let { trackEntry ->
                    val macroTrack = trackEntry.track

                    // Usar el método alternativo de búsqueda directa por Q con el track de macro_result_list
                    val lyricsByQResponse = lyricsProviders.getMusixmatchLyricsByQ(macroTrack, userToken!!)
                    val lyricsByQ = MusixmatchLyricsParser.parseLyrics(lyricsByQResponse)

                    if (lyricsByQ.isNotBlank()) {
                        // Si hay traducción disponible, añadirla
                        val translation = getTranslation(App.instance, macroTrack.track_id.toString())
                        if (translation != null) {
                            return@withContext "$lyricsByQ\n\n--- TRADUCCIÓN ---\n\n$translation"
                        }

                        return@withContext lyricsByQ
                    }
                }
                
                // Si llegamos aquí, intentamos una última vez con el parser general
                val lyrics = MusixmatchLyricsParser.parseLyrics(fixResponse)
                if (lyrics.isNotBlank()) {
                    return@withContext lyrics
                }
                
                throw Exception("No se encontraron letras en Musixmatch")
                  } catch (e: Exception) {
                // Check if this is a CAPTCHA or JSON parsing error related to CAPTCHA
                if (e.message?.contains("Expected start of the object '{', but had '['") == true || 
                    e.stackTraceToString().contains("hint\":\"captcha")) {
                    Timber.w("CAPTCHA challenge detected in lyrics request: ${e.message}")
                    handleCaptchaDetected()
                    throw IllegalStateException("No se pudieron obtener letras de Musixmatch. El servicio requiere verificación CAPTCHA. Por favor, inténtelo más tarde.", e)
                } else {
                    Timber.e(e, "Error al obtener letras de Musixmatch")
                    reportException(e)
                    throw e
                }
            }
        }
    }
      // Implementación para retornar múltiples resultados cuando hay ambigüedad
    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        try {
            // Ensure we're authenticated before making any requests
            ensureInitialized(App.instance)
            if (!isUserAuthenticated && !checkAuthentication(App.instance)) {
                Timber.e("No se pudo autenticar con Musixmatch para obtener múltiples resultados")
                // Try with standard method as fallback
                getLyrics(id, title, artist, duration).onSuccess(callback)
                return
            }
            
            // Intenta obtener múltiples resultados
            val results = getMultipleSearchResults(App.instance, title, artist).getOrNull()
            
            if (results != null && results.isNotEmpty()) {
                // Limita a un número razonable de resultados para evitar demasiadas llamadas
                val maxResults = App.instance.dataStore[MUSIXMATCH_RESULTS_MAX]?.toIntOrNull() ?: 3
                val limitedResults = results.take(maxResults)
                
                // Para cada resultado, intenta obtener las letras
                for (track in limitedResults) {
                    val trackId = track.track_id.toString()
                    
                    try {
                        // Make sure we have a valid token
                        if (userToken == null) {
                            if (!refreshToken(App.instance)) {
                                continue // Skip this track if we can't refresh token
                            }
                        }
                        
                        // Obtiene las letras para este track
                        val lyricsResponse = lyricsProviders.getMusixmatchLyrics(trackId, userToken!!)
                        val lyrics = MusixmatchLyricsParser.parseLyrics(lyricsResponse)
                        
                        if (lyrics.isNotBlank()) {
                            // Si hay traducción disponible y el usuario la ha habilitado, añadirla
                            val translation = getTranslation(App.instance, trackId)
                            
                            val result = if (translation != null) {
                                "$lyrics\n\n--- TRADUCCIÓN ---\n\n$translation"
                            } else {
                                lyrics
                            }
                            
                            // Devuelve este resultado
                            callback(result)
                        } else {
                            // Si no hay letras sincronizadas, intenta con letras no sincronizadas
                            val unsyncedResponse = lyricsProviders.getMusixmatchUnsyncedLyrics(trackId, userToken!!)
                            val unsyncedLyrics = MusixmatchLyricsParser.parseLyrics(unsyncedResponse)
                            
                            if (unsyncedLyrics.isNotBlank()) {
                                callback(unsyncedLyrics)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error al obtener letras para track $trackId")
                    }
                }
            } else {
                // Si no hay múltiples resultados, usa el método estándar
                getLyrics(id, title, artist, duration).onSuccess(callback)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al obtener múltiples letras")
            
            // Como fallback, intenta el método estándar
            getLyrics(id, title, artist, duration).onSuccess(callback)
        }
    }
}

object Android {
    private lateinit var applicationContext: Context
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}
