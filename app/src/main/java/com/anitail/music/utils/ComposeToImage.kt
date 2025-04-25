package com.anitail.music.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import coil.ImageLoader
import coil.request.ImageRequest
import com.anitail.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ComposeToImage {

    /**
     * Crea una imagen de letra de canción usando Canvas directamente (sin Compose)
     * Este método es más confiable que intentar renderizar un composable
     */    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        isDarkTheme: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val backgroundColor = 0xFF121212.toInt()
        val cardBackgroundColor = 0xFF242424.toInt()
        val textColor = 0xFFFFFFFF.toInt()
        val secondaryTextColor = 0xB3FFFFFF.toInt()
        val spotifyGreen = 0xFF1DB954.toInt()
        val cornerRadius = 64f

        val maxCardWidth = width - 32
        val maxCardHeight = height - 32
        val cardWidth = minOf(maxCardWidth, (maxCardHeight / 0.56f).toInt())
        val cardHeight = (cardWidth * 0.56f).toInt()
        val bitmap = createBitmap(cardWidth, cardHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        var dynamicBgColor = backgroundColor
        var dynamicCardColor = cardBackgroundColor
        var dynamicAccentColor = spotifyGreen
        var coverArtBitmap: Bitmap? = null
        if (coverArtUrl != null) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .size(256)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                coverArtBitmap = result.drawable?.toBitmap(256, 256, Bitmap.Config.ARGB_8888)
                if (coverArtBitmap != null) {
                    val palette = androidx.palette.graphics.Palette.from(coverArtBitmap!!).generate()
                    val dominant = palette.getDominantColor(backgroundColor)
                    val light = palette.getLightVibrantColor(cardBackgroundColor)
                    val vibrant = palette.getVibrantColor(spotifyGreen)
                    dynamicBgColor = dominant
                    dynamicCardColor = light
                    dynamicAccentColor = vibrant
                }
            } catch (_: Exception) {}
        }

        val cardRect = android.graphics.RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat())
        val gradientPaint = android.graphics.Paint()
        val gradient = android.graphics.LinearGradient(
            0f, 0f, cardWidth.toFloat(), cardHeight.toFloat(),
            intArrayOf(dynamicBgColor, dynamicCardColor, dynamicBgColor),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        gradientPaint.shader = gradient
        canvas.drawRect(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat(), gradientPaint)

        val shadowPaint = android.graphics.Paint().apply {
            color = 0x33000000
            setShadowLayer(32f, 0f, 16f, 0x99000000.toInt())
            isAntiAlias = true
        }
        canvas.drawRect(cardRect, shadowPaint)

        val cardPaint = android.graphics.Paint().apply {
            color = dynamicCardColor
            isAntiAlias = true
        }
        canvas.drawRect(cardRect, cardPaint)

        val innerPadding = (cardHeight * 0.08f).toInt()
        val coverArtSize = (cardHeight * 0.22f).toInt()
        val coverArtLeft = innerPadding.toFloat()
        val coverArtTop = innerPadding.toFloat()
        val textLeft = coverArtLeft + coverArtSize + innerPadding * 0.7f
        val textWidth = cardWidth - textLeft - innerPadding

        val coverArtShadowPaint = android.graphics.Paint().apply {
            color = 0x22000000
            setShadowLayer(8f, 0f, 4f, 0x55000000)
            isAntiAlias = true
        }
        canvas.withTranslation(4f, 6f) {
            val coverRectF = android.graphics.RectF(
                coverArtLeft,
                coverArtTop,
                coverArtLeft + coverArtSize,
                coverArtTop + coverArtSize
            )
            drawRoundRect(
                coverRectF,
                coverArtSize * 0.18f,
                coverArtSize * 0.18f,
                coverArtShadowPaint
            )
        }
        if (coverArtUrl != null) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .size(coverArtSize)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                val coverArt = result.drawable?.toBitmap(coverArtSize, coverArtSize, Bitmap.Config.ARGB_8888)
                if (coverArt != null) {
                    val rectF = android.graphics.RectF(coverArtLeft, coverArtTop, coverArtLeft + coverArtSize, coverArtTop + coverArtSize)
                    val path = android.graphics.Path().apply {
                        addRoundRect(rectF, coverArtSize * 0.18f, coverArtSize * 0.18f, android.graphics.Path.Direction.CW)
                    }
                    canvas.withClip(path) {
                        drawBitmap(coverArt, coverArtLeft, coverArtTop, null)
                    }
                }
            } catch (_: Exception) {}
        }

        val titlePaint = TextPaint().apply {
            color = textColor
            textSize = cardHeight * 0.08f // Más pequeño
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
        }
        val maxTitleChars = 38
        val titleText = if (songTitle.length > maxTitleChars) songTitle.take(maxTitleChars - 3) + "..." else songTitle
        val titleLayout = StaticLayout.Builder.obtain(
            titleText, 0, titleText.length, titlePaint, textWidth.toInt()
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
         .setIncludePad(false)
         .setMaxLines(1)
         .setEllipsize(android.text.TextUtils.TruncateAt.END)
         .build()
        val artistPaint = TextPaint().apply {
            color = secondaryTextColor
            textSize = cardHeight * 0.05f // Más pequeño
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }
        val artistLayout = StaticLayout.Builder.obtain(
            artistName, 0, artistName.length, artistPaint, textWidth.toInt()
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
         .setIncludePad(false)
         .setMaxLines(1)
         .setEllipsize(android.text.TextUtils.TruncateAt.END)
         .build()
        val textBlockTop = coverArtTop + 2f
        canvas.withTranslation(textLeft, textBlockTop) {
            titleLayout.draw(this)
            val underlineY = titleLayout.height + 6f
            val underlinePaint = android.graphics.Paint().apply {
                color = dynamicAccentColor
                strokeWidth = 6f
                isAntiAlias = true
            }
            drawLine(0f, underlineY, titleLayout.width.toFloat() * 0.7f, underlineY, underlinePaint)
            this.translate(0f, titleLayout.height + 16f)
            artistLayout.draw(this)
        }

        val lyricsPaint = TextPaint().apply {
            color = textColor
            textSize = cardHeight * 0.09f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
            setShadowLayer(8f, 0f, 4f, 0x88000000.toInt())
        }
        val lyricsMaxWidth = cardWidth - innerPadding * 2 - 24
        val lyricsLayout = StaticLayout.Builder.obtain(
            lyrics, 0, lyrics.length, lyricsPaint, lyricsMaxWidth
        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
         .setIncludePad(false)
         .setMaxLines(3)
         .setEllipsize(android.text.TextUtils.TruncateAt.END)
         .build()
        val lyricsY = coverArtTop + coverArtSize + innerPadding * 1.2f
        val lyricsHeight = lyricsLayout.height
        val logoBlockHeight = (cardHeight * 0.08f).toInt()
        val availableSpace = cardHeight - (lyricsY + logoBlockHeight + innerPadding)
        val lyricsYOffset = lyricsY + availableSpace / 2f - lyricsHeight / 2f
        canvas.withTranslation((cardWidth - lyricsMaxWidth) / 2f, lyricsYOffset) {
            lyricsLayout.draw(this)
        }

        val appLogo = androidx.core.content.res.ResourcesCompat.getDrawable(
            context.resources, context.resources.getIdentifier("lyrics", "drawable", context.packageName), null
        )?.toBitmap(logoBlockHeight, logoBlockHeight)
        val appName =  context.getString(R.string.app_name)
        val appNamePaint = TextPaint().apply {
            color = textColor
            textSize = cardHeight * 0.045f // Mucho más pequeño
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.01f
            setShadowLayer(6f, 0f, 0f, 0x33FFFFFF)
        }
        val logoY = cardHeight - innerPadding - logoBlockHeight / 2f
        var logoDrawn = false
        if (appLogo != null) {
            canvas.drawBitmap(appLogo, innerPadding.toFloat(), logoY, null)
            logoDrawn = true
        }
        val textX = if (logoDrawn) innerPadding.toFloat() + logoBlockHeight + 12f else innerPadding.toFloat()
        val textY = cardHeight - innerPadding - logoBlockHeight / 2f + logoBlockHeight * 0.7f
        canvas.drawText(appName, textX, textY, appNamePaint)

        return@withContext bitmap
    }    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnitailMusic")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile
            )
        }
    }
}