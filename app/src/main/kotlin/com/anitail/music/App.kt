package com.anitail.music

import android.app.Application
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.RequiresApi
import androidx.datastore.preferences.core.edit
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.anitail.innertube.YouTube
import com.anitail.innertube.models.YouTubeLocale
import com.anitail.kugou.KuGou
import com.anitail.music.constants.AccountChannelHandleKey
import com.anitail.music.constants.AccountEmailKey
import com.anitail.music.constants.AccountNameKey
import com.anitail.music.constants.ContentCountryKey
import com.anitail.music.constants.ContentLanguageKey
import com.anitail.music.constants.CountryCodeToName
import com.anitail.music.constants.DataSyncIdKey
import com.anitail.music.constants.InnerTubeCookieKey
import com.anitail.music.constants.LanguageCodeToName
import com.anitail.music.constants.MaxImageCacheSizeKey
import com.anitail.music.constants.ProxyEnabledKey
import com.anitail.music.constants.ProxyTypeKey
import com.anitail.music.constants.ProxyUrlKey
import com.anitail.music.constants.SYSTEM_DEFAULT
import com.anitail.music.constants.UseLoginForBrowse
import com.anitail.music.constants.VisitorDataKey
import com.anitail.music.extensions.toEnum
import com.anitail.music.extensions.toInetSocketAddress
import com.anitail.music.services.AutoBackupWorker
import com.anitail.music.services.UpdateCheckWorker
import com.anitail.music.utils.dataStore
import com.anitail.music.utils.get
import com.anitail.music.utils.reportException
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    @javax.inject.Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @RequiresApi(Build.VERSION_CODES.M)
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.plant(Timber.DebugTree())
        
        // OneSignal initialization
        // TODO: Replace with your real OneSignal App ID
        val oneSignalAppId = "8f09b52f-d61a-469e-9d7d-203ebf6b9e1b"
        try {
            // Enable verbose logging for debugging (remove in production)
          OneSignal.Debug.logLevel = LogLevel.VERBOSE
           OneSignal.initWithContext(this, oneSignalAppId)
            GlobalScope.launch(Dispatchers.IO) {
                OneSignal.Notifications.requestPermission(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "OneSignal initialization failed")
        }
          // Inicializar datos de autenticación de Musixmatch con una política de reintentos
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val success = com.anitail.music.lyrics.MusixmatchLyricsProvider.loadSavedAuthData(this@App)
                if (success) {
                    Timber.d("Musixmatch authentication data loaded successfully")
                } else {
                    Timber.w("Musixmatch authentication data could not be loaded completely. Will retry on demand.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Musixmatch authentication data")
            }
        }
        
        // Initialize automatic update checks
        UpdateCheckWorker.schedule(this)
        
        // Initialize automatic backup (only schedule, don't run immediately)
        try {
            AutoBackupWorker.schedulePeriodicOnly(this)
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule auto backups")
        }

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] != false) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().onFailure {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@App, "Failed to get visitorData.", LENGTH_SHORT).show()
                            }
                            reportException(it)
                        }.getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[DataSyncIdKey] }
                .distinctUntilChanged()
                .collect { dataSyncId ->
                    YouTube.dataSyncId = dataSyncId?.let {
                        /*
                         * Workaround to avoid breaking older installations that have a dataSyncId
                         * that contains "||" in it.
                         * If the dataSyncId ends with "||" and contains only one id, then keep the
                         * id before the "||".
                         * If the dataSyncId contains "||" and is not at the end, then keep the
                         * second id.
                         * This is needed to keep using the same account as before.
                         */
                        it.takeIf { !it.contains("||") }
                            ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                            ?: it.substringAfter("||")
                    }
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        // we now allow user input now, here be the demons. This serves as a last ditch effort to avoid a crash loop
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        forgetAccount(this@App)
                    }
                }
        }
    }
    override fun newImageLoader(): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        // will crash app if you set to 0 after cache starts being used
        if (cacheSize == 0) {
            return ImageLoader.Builder(this)
                .crossfade(true)
                .respectCacheHeaders(false)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                .diskCachePolicy(CachePolicy.DISABLED)
                .components {
                    // Add the GIF decoder
                    add(coil.decode.GifDecoder.Factory())
                }
                .build()
        }

        return ImageLoader.Builder(this)
        .crossfade(true)
        .respectCacheHeaders(false)
        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        .components {
            // Add the GIF decoder
            add(coil.decode.GifDecoder.Factory())
        }
        .diskCache(
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes((cacheSize ?: 512) * 1024 * 1024L)
                .build()
        )
        .build()
    }

    companion object {
        lateinit var instance: App
            private set

        fun forgetAccount(context: Context) {
            runBlocking {
                context.dataStore.edit { settings ->
                    settings.remove(InnerTubeCookieKey)
                    settings.remove(VisitorDataKey)
                    settings.remove(DataSyncIdKey)
                    settings.remove(AccountNameKey)
                    settings.remove(AccountEmailKey)
                    settings.remove(AccountChannelHandleKey)
                }
            }
        }
    }
}
