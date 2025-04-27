package com.anitail.music.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.anitail.music.R
import com.anitail.music.models.MediaMetadata

@Composable
fun rememberAdjustedFontSize(
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
    density: Density,
    initialFontSize: TextUnit = 20.sp,
    minFontSize: TextUnit = 14.sp,
    style: TextStyle = TextStyle.Default,
    textMeasurer: androidx.compose.ui.text.TextMeasurer? = null
): TextUnit {
    val measurer = textMeasurer ?: rememberTextMeasurer()

    var calculatedFontSize by remember(text, maxWidth, maxHeight, style, density) {
        val initialSize = when {
            text.length < 50 -> initialFontSize
            text.length < 100 -> (initialFontSize.value * 0.8f).sp
            text.length < 200 -> (initialFontSize.value * 0.6f).sp
            else -> (initialFontSize.value * 0.5f).sp
        }
        mutableStateOf(initialSize)
    }

    LaunchedEffect(key1 = text, key2 = maxWidth, key3 = maxHeight) {
        val targetWidthPx = with(density) { maxWidth.toPx() * 0.92f }
        val targetHeightPx = with(density) { maxHeight.toPx() * 0.92f }
        if (text.isBlank()) {
            calculatedFontSize = minFontSize
            return@LaunchedEffect
        }

        // For very short lyrics, we can use a larger font size
        if (text.length < 20) {
            val largerSize = (initialFontSize.value * 1.1f).sp
            val result = measurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = largerSize)
            )
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                calculatedFontSize = largerSize
                return@LaunchedEffect
            }
        } else if (text.length < 30) {
            val largerSize = (initialFontSize.value * 0.9f).sp
            val result = measurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = largerSize)
            )
            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                calculatedFontSize = largerSize
                return@LaunchedEffect
            }
        }

        var minSize = minFontSize.value
        var maxSize = initialFontSize.value
        var bestFit = minSize
        var iterations = 0

        while (minSize <= maxSize && iterations < 20) {
            iterations++
            val midSize = (minSize + maxSize) / 2
            val midSizeSp = midSize.sp

            val result = measurer.measure(
                text = AnnotatedString(text),
                style = style.copy(fontSize = midSizeSp)
            )

            if (result.size.width <= targetWidthPx && result.size.height <= targetHeightPx) {
                bestFit = midSize
                minSize = midSize + 0.5f
            } else {
                maxSize = midSize - 0.5f
            }
        }

        calculatedFontSize = if (bestFit < minFontSize.value) minFontSize else bestFit.sp
    }

    return calculatedFontSize
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LyricsImageCard(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    darkBackground: Boolean = true,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    secondaryTextColor: Color? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backgroundGradient = backgroundColor ?: if (darkBackground) Color(0xFF121212) else Color(0xFFF5F5F5)
    val mainTextColor = textColor ?: if (darkBackground) Color.White else Color.Black
    val secondaryColor = secondaryTextColor ?: if (darkBackground) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(mediaMetadata.thumbnailUrl)            .crossfade(true)
            .placeholder(R.drawable.music_note)
            .error(R.drawable.music_note)
            .precision(Precision.EXACT)
            .build()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 440.dp)
                .background(
                    color = backgroundGradient,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {                    Image(
                        painter = painter,
                        contentDescription = "Album Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                spotColor = mainTextColor.copy(alpha = 0.3f)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = mainTextColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mediaMetadata.title,
                            color = mainTextColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = mediaMetadata.artists.joinToString { it.name },
                            color = secondaryColor,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Lyrics
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val textStyle = TextStyle(
                        color = mainTextColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.005.em,
                    )

                    val textMeasurer = rememberTextMeasurer()
                    val initialSize = when {
                        lyricText.length < 50 -> 52.sp
                        lyricText.length < 100 -> 42.sp
                        lyricText.length < 200 -> 34.sp
                        lyricText.length < 300 -> 28.sp
                        else -> 26.sp
                    }

                    val dynamicFontSize = rememberAdjustedFontSize(
                        text = lyricText,
                        maxWidth = availableWidth - 16.dp,
                        maxHeight = availableHeight - 8.dp,
                        density = density,
                        initialFontSize = initialSize,
                        minFontSize = 22.sp,
                        style = textStyle,
                        textMeasurer = textMeasurer
                    )

                    Text(
                        text = lyricText,
                        style = textStyle.copy(
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicFontSize.value.sp * 1.2f
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = "Anitail Logo",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.app_name),
                        color = secondaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
