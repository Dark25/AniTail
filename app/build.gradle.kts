@file:Suppress("UnstableApiUsage")

import java.util.Properties


plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.anitail.music"
    compileSdk = 36
    ndkVersion = "25.1.8937393"
    defaultConfig {
        applicationId = "com.anitail.music"
        minSdk = 21
        targetSdk = 36
        versionCode = 8
        versionName = "1.7.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        
        // Last.fm API credentials from local.properties or environment variables
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        
        val lastfmApiKey = localProperties.getProperty("LASTFM_API_KEY") 
            ?: System.getenv("LASTFM_API_KEY") 
            ?: "PUT_YOUR_API_KEY_HERE"
        val lastfmApiSecret = localProperties.getProperty("LASTFM_API_SECRET") 
            ?: System.getenv("LASTFM_API_SECRET") 
            ?: "PUT_YOUR_API_SECRET_HERE"
            
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"$lastfmApiSecret\"")
    }

    flavorDimensions += "abi"
    productFlavors {
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            //noinspection ChromeOsAbiSupport
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = file("persistent-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("persistentDebug")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        disable += "MissingTranslation"
        disable += "MissingQuantity"
        disable += "ImpliedQuantity"
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.coil)
    implementation(libs.coil.gif)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.squigglyslider)

    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.jsoup)
    kapt(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)
    implementation(projects.kizzy)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.json)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.compose.icons.extended)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)    // OneSignal Push Notifications
    implementation(libs.onesignal)
    
    // Last.fm integration
    implementation(libs.lastfm.java)
    implementation(libs.commons.codec)
}
