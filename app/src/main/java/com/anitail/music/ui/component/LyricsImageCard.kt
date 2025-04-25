package com.anitail.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.anitail.music.R
import com.anitail.music.models.MediaMetadata

/**
 * A composable that shows the lyrics of a song along with its cover art and title
 * This is specifically designed to be captured as an image for sharing
 */
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
    val backgroundGradient = backgroundColor ?: if (darkBackground) Color(0xFF121212) else Color(0xFFF5F5F5)
    val mainTextColor = textColor ?: if (darkBackground) Color.White else Color.Black
    val secondaryColor = secondaryTextColor ?: if (darkBackground) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(mediaMetadata.thumbnailUrl)
            .crossfade(true)
            .build()
    )
    Box(
        modifier = Modifier
            .size( width = 1080.dp, height = 1080.dp)
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .background(
                    color = backgroundGradient,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 28.dp, vertical = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painter,
                        contentDescription = "Album Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
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
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = lyricText,
                    color = mainTextColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
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
