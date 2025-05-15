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
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    private val lyricsProviders by lazy {
        LyricsProviders(App.instance, json)
    }    /**
     * Carga los datos de autenticación guardados en DataStore
     */
    suspend fun loadSavedAuthData(context: Context) {
        try {
            val savedToken = context.dataStore.data.first()[MUSIXMATCH_USER_TOKEN]
            val savedCookie = context.dataStore.data.first()[MUSIXMATCH_COOKIE]
            val savedTimestamp = context.dataStore.data.first()[MUSIXMATCH_TOKEN_TIMESTAMP]?.toLongOrNull() ?: 0L
            val isAuthenticated = context.dataStore.data.first()[MUSIXMATCH_AUTHENTICATED] ?: false
            
            if (!savedToken.isNullOrEmpty() && !savedCookie.isNullOrEmpty() && isAuthenticated) {
                userToken = savedToken
                lyricsProviders.musixmatchUserToken = savedToken
                lyricsProviders.musixmatchCookie = savedCookie
                lastTokenFetchTimestamp = savedTimestamp
                isUserAuthenticated = isAuthenticated
                Timber.d("Musixmatch credenciales cargadas desde DataStore")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al cargar los datos de autenticación de Musixmatch")
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
    }
      /**
     * Inicia sesión con la cuenta de Musixmatch
     */
    suspend fun login(context: Context): Result<Boolean> = runCatching {
        val email = context.dataStore.data.first()[MUSIXMATCH_EMAIL]
        val password = context.dataStore.data.first()[MUSIXMATCH_PASSWORD]
        
        if (email == null || password == null) {
            return@runCatching false
        }          // Obtener el token de usuario si aún no lo tenemos
        if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 6 * 60 * 60 * 1000) {
            // Intentar obtener el token, manejar posibles errores
            try {
                val tokenResponse = lyricsProviders.getMusixmatchUserToken()
                val tokenResponseText = tokenResponse.bodyAsText()
                Timber.d("Musixmatch token received: $tokenResponseText")
                
                // Extraer el token
                userToken = MusixmatchLyricsParser.getToken(tokenResponse)
                Timber.d("Token extraído: $userToken")
                
                // Verificar que el token no esté vacío
                if (userToken.isNullOrEmpty()) {
                    Timber.e("No se pudo extraer el token de Musixmatch de la respuesta. Respuesta completa: $tokenResponseText")
                    return@runCatching false
                }
                
                lyricsProviders.musixmatchUserToken = userToken
                lastTokenFetchTimestamp = System.currentTimeMillis()
                
                // Guardar el nuevo token en DataStore
                context.dataStore.edit { preferences ->
                    preferences[MUSIXMATCH_USER_TOKEN] = userToken!!
                    preferences[MUSIXMATCH_TOKEN_TIMESTAMP] = lastTokenFetchTimestamp.toString()
                }
                
                Timber.d("Musixmatch token refreshed: $userToken")
            } catch (e: Exception) {
                Timber.e(e, "Error al obtener el token de Musixmatch")
                return@runCatching false
            }
        }// Intenta iniciar sesión con las credenciales
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
        return@runCatching isSuccess
    }
      /**
     * Obtiene la información sobre la última renovación de la sesión
     */
    suspend fun getSessionInfo(context: Context): Pair<Boolean, Long?> {
        val isAuthenticated = context.dataStore.data.first()[MUSIXMATCH_AUTHENTICATED] ?: false
        val timestamp = context.dataStore.data.first()[MUSIXMATCH_TOKEN_TIMESTAMP]?.toLongOrNull()
        return Pair(isAuthenticated, timestamp)
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
     */
    private suspend fun checkAuthentication(context: Context): Boolean {
        // Si ya sabemos que está autenticado, no es necesario comprobar
        if (isUserAuthenticated) {
            return true
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
            if (System.currentTimeMillis() - lastTokenFetchTimestamp > 6 * 60 * 60 * 1000) {
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
    }

    /**
     * Obtiene la traducción de las letras en el idioma preferido
     */
    private suspend fun getTranslation(context: Context, trackId: String): String? {
        if (!isUserAuthenticated) {
            if (!checkAuthentication(context)) {
                return null
            }
        }
        
        val preferredLanguage = context.dataStore.data.first()[MUSIXMATCH_PREFERRED_LANGUAGE] ?: "es"
        val showTranslations = context.dataStore.data.first()[MUSIXMATCH_SHOW_TRANSLATIONS] ?: false
        
        if (!showTranslations) {
            return null
        }
          return try {
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
            // Si no tenemos token o cookie, intentar cargar los datos guardados
            if (userToken == null && !isUserAuthenticated) {
                loadSavedAuthData(context)
            }
            
            // Refrescar el token si han pasado más de 6 horas desde la última obtención o es nulo
            if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 6 * 60 * 60 * 1000) {
                val tokenResponse = lyricsProviders.getMusixmatchUserToken()
                userToken = MusixmatchLyricsParser.getToken(tokenResponse)
                lyricsProviders.musixmatchUserToken = userToken
                lastTokenFetchTimestamp = System.currentTimeMillis()
                
                // Guardar el token actualizado
                context.dataStore.edit { preferences ->
                    preferences[MUSIXMATCH_USER_TOKEN] = userToken!!
                    preferences[MUSIXMATCH_TOKEN_TIMESTAMP] = lastTokenFetchTimestamp.toString()
                }
                
                Timber.d("Musixmatch token refreshed: $userToken")
            }

            // Buscar la canción utilizando el título y artista
            val searchResponse = lyricsProviders.searchMusixmatchTrackId(
                q = "$artist $title",
                userToken = userToken ?: throw IllegalStateException("No se pudo obtener el token de Musixmatch")
            )

            // Aquí extraeríamos la lista de resultados múltiples
            // Nota: Esto es un ejemplo, la estructura real dependerá de la respuesta de la API
            val results = mutableListOf<SearchMusixmatchResponse.Message.Body.Track.TrackX>()
            
            try {
                val jsonResponse = searchResponse.body<SearchMusixmatchResponse>()
                
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
                // Si no tenemos token o cookie, intentar cargar los datos guardados
                if (userToken == null && !isUserAuthenticated) {
                    loadSavedAuthData(App.instance)
                }
                
                // Refrescar el token si han pasado más de 6 horas desde la última obtención o es nulo
                if (userToken == null || System.currentTimeMillis() - lastTokenFetchTimestamp > 6 * 60 * 60 * 1000) {
                    val tokenResponse = lyricsProviders.getMusixmatchUserToken()
                    userToken = MusixmatchLyricsParser.getToken(tokenResponse)
                    lyricsProviders.musixmatchUserToken = userToken
                    lastTokenFetchTimestamp = System.currentTimeMillis()
                    
                    // Guardar el token actualizado
                    App.instance.dataStore.edit { preferences ->
                        preferences[MUSIXMATCH_USER_TOKEN] = userToken!!
                        preferences[MUSIXMATCH_TOKEN_TIMESTAMP] = lastTokenFetchTimestamp.toString()
                    }
                    
                    Timber.d("Musixmatch token refreshed: $userToken")
                }

                // Buscar la canción utilizando el título y artista
                val searchResponse = lyricsProviders.searchMusixmatchTrackId(
                    q = "$artist $title",
                    userToken = userToken ?: throw IllegalStateException("No se pudo obtener el token de Musixmatch")
                )

                // Extraer el ID de la pista de la respuesta de búsqueda
                val trackId = MusixmatchLyricsParser.getFirstTrackId(searchResponse)
                
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
                Timber.e(e, "Error al obtener letras de Musixmatch")
                reportException(e)
                throw e
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
