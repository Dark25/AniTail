package com.anitail.music.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.anitail.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ComposeToImage {

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        textColor: Int? = null,
        secondaryTextColor: Int? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val defaultBackgroundColor = 0xFF121212.toInt()
        val defaultCardBackgroundColor = 0xFF242424.toInt()
        val defaultTextColor = 0xFFFFFFFF.toInt()
        val defaultSecondaryTextColor = 0xB3FFFFFF.toInt()
        val spotifyGreen = 0xFF1DB954.toInt()

        val maxCardWidth = width - 32
        val maxCardHeight = height - 32
        val cardWidth = minOf(maxCardWidth, (maxCardHeight / 0.56f).toInt())
        val cardHeight = (cardWidth * 0.56f).toInt()
        val bitmap = createBitmap(cardWidth, cardHeight)
        val canvas = Canvas(bitmap)

        val bgColor = backgroundColor ?: defaultBackgroundColor
        val cardBgColor = backgroundColor ?: defaultCardBackgroundColor
        val mainTextColor = textColor ?: defaultTextColor
        val secondaryTxtColor = secondaryTextColor ?: defaultSecondaryTextColor

        canvas.drawColor(bgColor)
        var dynamicAccentColor = spotifyGreen
        var coverArtBitmap: Bitmap? = null
        
        if (coverArtUrl != null) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .size(512) // Request larger image for better quality
                    .allowHardware(false)
                    .precision(Precision.EXACT) // Request exact size for best quality
                    .memoryCacheKey(coverArtUrl) // Use URL as cache key
                    .diskCacheKey(coverArtUrl)
                    .build()
            val result = imageLoader.execute(request)
            coverArtBitmap = result.drawable?.toBitmap(512, 512, Bitmap.Config.ARGB_8888)
            
            coverArtBitmap?.let {                // Enhanced color extraction with more sophisticated palette analysis
                val palette = Palette.Builder(it)
                    .maximumColorCount(32) // Get more colors for better selection
                    .generate()

                // Try different color profiles with intelligent fallbacks
                // First try vibrant colors, then light vibrant, then dominant
                val vibrantColor = palette.getVibrantColor(Color.TRANSPARENT)
                val lightVibrantColor = palette.getLightVibrantColor(Color.TRANSPARENT)
                val dominantColor = palette.getDominantColor(Color.TRANSPARENT)
                val mutedColor = palette.getMutedColor(Color.TRANSPARENT)

                dynamicAccentColor = when {
                    vibrantColor != Color.TRANSPARENT -> vibrantColor
                    lightVibrantColor != Color.TRANSPARENT -> lightVibrantColor
                    dominantColor != Color.TRANSPARENT -> dominantColor
                    mutedColor != Color.TRANSPARENT -> mutedColor
                    else -> spotifyGreen
                }

                // Ensure the color has good contrast with background
                val contrastRatio = ColorUtils.calculateContrast(dynamicAccentColor, cardBgColor)
                if (contrastRatio < 3.0) {
                    // If contrast is poor, adjust the color to improve visibility
                    val hsv = FloatArray(3)
                    Color.colorToHSV(dynamicAccentColor, hsv)
                    hsv[1] = 1.0f.coerceAtMost(hsv[1] + 0.3f) // Increase saturation
                    hsv[2] = 1.0f.coerceAtMost(hsv[2] + 0.2f) // Increase brightness if needed
                    dynamicAccentColor = Color.HSVToColor(hsv)
                }
            }
        } catch (_: Exception) {
            // If image loading fails, try to use a placeholder
            try {
                coverArtBitmap = context.getDrawable(R.drawable.music_note)?.toBitmap(512, 512, Bitmap.Config.ARGB_8888)
            } catch (_: Exception) { }
        }
    }

        val cardRect = RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat())
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, cardWidth.toFloat(), cardHeight.toFloat(),
                intArrayOf(bgColor, cardBgColor, bgColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(cardRect, gradientPaint)

        val shadowPaint = Paint().apply {
            color = 0x33000000
            setShadowLayer(32f, 0f, 16f, 0x99000000.toInt())
            isAntiAlias = true
        }
        canvas.drawRect(cardRect, shadowPaint)
        
        val cardPaint = Paint().apply {
            color = cardBgColor
            isAntiAlias = true
        }
        canvas.drawRect(cardRect, cardPaint)

        // Add a subtle noise texture to the background
        try {
            val random = java.util.Random(songTitle.hashCode().toLong())
            val noisePaint = Paint().apply {
                color = 0x05FFFFFF
                style = Paint.Style.FILL
            }

            // Add some random dots for texture
            for (i in 0 until 300) {
                val x = random.nextFloat() * cardWidth
                val y = random.nextFloat() * cardHeight
                val size = random.nextFloat() * 2f + 0.5f
                canvas.drawCircle(x, y, size, noisePaint)
            }
        } catch (_: Exception) {
            // If texture generation fails, continue without it
        }

        val innerPadding = (cardHeight * 0.08f).toInt()
        // Increase cover art size for better visibility
        val coverArtSize = (cardHeight * 0.25f).toInt()
        val coverArtLeft = innerPadding.toFloat()
        val coverArtTop = innerPadding.toFloat()
        val textLeft = coverArtLeft + coverArtSize + innerPadding * 0.7f
        val textWidth = cardWidth - textLeft - innerPadding

        // Dibuja el Cover Art si existe
        coverArtBitmap?.let { cover ->
            val coverRect = RectF(
                coverArtLeft,
                coverArtTop,
                coverArtLeft + coverArtSize,
                coverArtTop + coverArtSize
            )

            // Improved corner radius for better appearance
            val cornerRadius = coverArtSize * 0.15f

            // Use a paint with shadow for more depth
            val shadowPaint = Paint().apply {
                isAntiAlias = true
                setShadowLayer(12f, 0f, 4f, 0x66000000)
            }
            canvas.drawRoundRect(coverRect, cornerRadius, cornerRadius, shadowPaint)

            // Create path for clipping with rounded corners
            val path = Path()
            path.addRoundRect(coverRect, cornerRadius, cornerRadius, Path.Direction.CW)

            // Clip with rounded corners and draw the bitmap
            canvas.withClip(path) {
                drawBitmap(cover, null, coverRect, null)
            }

            // Add a subtle border around the cover art
            val borderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(80, Color.red(dynamicAccentColor), Color.green(dynamicAccentColor), Color.blue(dynamicAccentColor))
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(coverRect, cornerRadius, cornerRadius, borderPaint)        }
        
        val titlePaint = TextPaint().apply {
            color = mainTextColor
            textSize = cardHeight * 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
            // Add a subtle shadow to the title
            setShadowLayer(4f, 1f, 1f, 0x40000000)
        }

        val maxTitleChars = 38
        val titleText = if (songTitle.length > maxTitleChars) songTitle.take(maxTitleChars - 3) + "..." else songTitle
        val titleLayout = StaticLayout.Builder.obtain(
            titleText, 0, titleText.length, titlePaint, textWidth.toInt()
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
            
        val artistPaint = TextPaint().apply {
            color = secondaryTxtColor
            textSize = cardHeight * 0.05f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
            // Add subtle letter spacing for a more elegant look
            letterSpacing = 0.01f
            // Add a very subtle shadow
            setShadowLayer(2f, 0.5f, 0.5f, 0x20000000)
        }
        val artistLayout = StaticLayout.Builder.obtain(
            artistName, 0, artistName.length, artistPaint, textWidth.toInt()
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
            
        canvas.withTranslation(textLeft, coverArtTop + 2f) {
            titleLayout.draw(this)
            
            // Gradient underline that fades out
            val underlineWidth = titleLayout.width.toFloat() * 0.8f
            val underlinePaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, underlineWidth, 0f,
                    intArrayOf(dynamicAccentColor, ColorUtils.setAlphaComponent(dynamicAccentColor, 0)),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                strokeWidth = 6f
                isAntiAlias = true
            }
            drawLine(0f, titleLayout.height + 6f, underlineWidth, titleLayout.height + 6f, underlinePaint)
            
            translate(0f, titleLayout.height + 16f)
            artistLayout.draw(this)
        }
        
        val lyricsMaxWidth = cardWidth - innerPadding * 2 - 24
        val lyricsTop = coverArtTop + coverArtSize + innerPadding * 1.2f
        val logoBlockHeight = (cardHeight * 0.08f).toInt()
        val lyricsBottom = cardHeight - (logoBlockHeight + innerPadding) - 16
        val availableLyricsHeight = lyricsBottom - lyricsTop
        
        val lyricsPaint = TextPaint().apply {
            color = mainTextColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.02f
            setShadowLayer(8f, 0f, 4f, 0x88000000.toInt())
            
            // Add a linear gradient shader for a nicer text effect if possible
            try {
                val textShader = LinearGradient(
                    0f, 0f, 0f, lyricsMaxWidth * 0.5f,
                    mainTextColor,
                    Color.argb(
                        Color.alpha(mainTextColor),
                        Color.red(mainTextColor),
                        Color.green(mainTextColor),
                        Color.blue(mainTextColor) - 10
                    ),
                    Shader.TileMode.CLAMP
                )
                setShader(textShader)
            } catch (_: Exception) {
                // If gradient fails, continue with solid color
            }
        }

        // --- Ajuste automático del tamaño de letra ---
        var lyricsTextSize = cardHeight * 0.07f
        lyricsPaint.textSize = lyricsTextSize

        var lyricsLayout: StaticLayout
        do {
            lyricsPaint.textSize = lyricsTextSize
            lyricsLayout = StaticLayout.Builder.obtain(                    lyrics, 0, lyrics.length, lyricsPaint, lyricsMaxWidth
            ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(30) // Increase max lines to show more lyrics content
                .build()

            if (lyricsLayout.height > availableLyricsHeight) {
                lyricsTextSize -= 2f
            } else {
                break
            }
        } while (lyricsTextSize > 20f)
        
        val lyricsYOffset = lyricsTop + (availableLyricsHeight - lyricsLayout.height) / 2f

        canvas.withTranslation((cardWidth - lyricsMaxWidth) / 2f, lyricsYOffset) {
            lyricsLayout.draw(this)
        }
        // --- Fin ajuste automático ---

        // Add a subtle vignette effect to focus attention on the content
        try {
            val vignetteRadius = cardWidth.coerceAtLeast(cardHeight) * 0.8f
            val vignetteColors = intArrayOf(
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(Color.BLACK, 40)
            )
            val vignettePositions = floatArrayOf(0.65f, 1.0f)

            val vignettePaint = Paint().apply {
                shader = RadialGradient(                    cardWidth / 2f,
                    cardHeight / 2f,
                    vignetteRadius,
                    vignetteColors,
                    vignettePositions,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(cardRect, vignettePaint)
        } catch (_: Exception) {
            // If vignette creation fails, continue without it
        }
        
        val appLogo = context.getDrawable(R.drawable.lyrics)?.toBitmap(
            logoBlockHeight, logoBlockHeight, Bitmap.Config.ARGB_8888
        )
        val appName = context.getString(R.string.app_name)
        val appNamePaint = TextPaint().apply {
            color = secondaryTxtColor
            textSize = cardHeight * 0.042f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAntiAlias = true
            letterSpacing = 0.01f
            setShadowLayer(6f, 0f, 0f, 0x33FFFFFF)
        }

        // Position the logo at the bottom right instead of bottom left
        // This balances the design better and makes it less distracting
        val logoX = cardWidth - innerPadding - (logoBlockHeight / 1.5f)
        val logoY = cardHeight - innerPadding - logoBlockHeight / 2f
        appLogo?.let {
            canvas.drawBitmap(it, logoX, logoY, null)
        }

        // Position app name to the left of the logo
        val textX = logoX - (appNamePaint.measureText(appName) + innerPadding * 0.5f)
        val textY = cardHeight - innerPadding - logoBlockHeight / 2f + logoBlockHeight * 0.5f
        canvas.drawText(appName, textX, textY, appNamePaint)

        return@withContext bitmap
    }

    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
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
