package com.anitail.music.utils

import android.util.Base64
import com.anitail.music.BuildConfig

object LastFmKeyManager {
    
    /**
     * Obtiene la API key de forma segura
     * Primero intenta usar BuildConfig, luego las keys ofuscadas como fallback
     */
    fun getApiKey(): String {
        return if (BuildConfig.LASTFM_API_KEY != "PUT_YOUR_API_KEY_HERE") {
            BuildConfig.LASTFM_API_KEY
        } else {
            getObfuscatedApiKey()
        }
    }

    /**
     * Obtiene el API secret de forma segura
     */
    fun getApiSecret(): String {
        return if (BuildConfig.LASTFM_API_SECRET != "PUT_YOUR_API_SECRET_HERE") {
            BuildConfig.LASTFM_API_SECRET
        } else {
            getObfuscatedApiSecret()
        }
    }

    /**
     * API Key ofuscada usando Base64 simple
     */
    private fun getObfuscatedApiKey(): String {
        return try {
            // Key ofuscada usando Base64
            val obfuscated = "NzMyYTkzNDdlNjk0MmE1NTVjODJmM2QxZDgxMmUyNjM="
            val decoded = Base64.decode(obfuscated, Base64.DEFAULT)
            String(decoded)
        } catch (e: Exception) {
            // Manejo de errores, si falla la decodificación retornamos un placeholder
            "PLACEHOLDER_API_KEY"
        }
    }

    /**
     * API Secret ofuscado usando Base64 simple
     */
    private fun getObfuscatedApiSecret(): String {
        return try {
            // Secret ofuscado usando Base64
            val obfuscated = "ZTI2MzA0MTI2MDNiNmYwY2FmYzI4YTVkZDFhY2Q5NTM="
            val decoded = Base64.decode(obfuscated, Base64.DEFAULT)
            String(decoded)
        } catch (e: Exception) {
            // Manejo de errores, si falla la decodificación retornamos un placeholder
            "PLACEHOLDER_API_SECRET"
        }
    }

    /**
     * Utilidad para generar strings ofuscados usando Base64
     */
    fun obfuscateString(input: String): String {
        return Base64.encodeToString(input.toByteArray(), Base64.DEFAULT).trim()
    }
}
