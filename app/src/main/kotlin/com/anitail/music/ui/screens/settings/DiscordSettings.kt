package com.anitail.music.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.LocalPlayerConnection
import com.anitail.music.R
import com.anitail.music.constants.DiscordInfoDismissedKey
import com.anitail.music.constants.DiscordNameKey
import com.anitail.music.constants.DiscordTokenKey
import com.anitail.music.constants.DiscordUsernameKey
import com.anitail.music.constants.EnableDiscordRPCKey
import com.anitail.music.db.entities.Song
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.component.PreferenceEntry
import com.anitail.music.ui.component.PreferenceGroupTitle
import com.anitail.music.ui.component.SwitchPreference
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.utils.rememberPreference
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)

    val coroutineScope = rememberCoroutineScope()

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var infoDismissed by rememberPreference(DiscordInfoDismissedKey, false)

    LaunchedEffect(discordToken) {
        val token = discordToken
        if (token.isEmpty()) {
            return@LaunchedEffect
        }
        coroutineScope.launch(Dispatchers.IO) {
            KizzyRPC.getUserInfo(token).onSuccess {
                discordUsername = it.username
                discordName = it.name
            }
        }
    }

    val (discordRPC, onDiscordRPCChange) = rememberPreference(
        key = EnableDiscordRPCKey,
        defaultValue = true
    )

    val isLoggedIn =
        remember(discordToken) {
            discordToken != ""
        }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        AnimatedVisibility(
            visible = !infoDismissed,
        ) {
            Card(
                colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                )

                Text(
                    text = stringResource(R.string.discord_information),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                TextButton(
                    onClick = {
                        infoDismissed = true
                    },
                    modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.account),
        )

        PreferenceEntry(
            title = {
                Text(
                    text = if (isLoggedIn) discordName else stringResource(R.string.not_logged_in),
                    modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                )
            },
            description =
            if (discordUsername.isNotEmpty()) {
                "@$discordUsername"
            } else {
                null
            },
            icon = { Icon(painterResource(R.drawable.discord), null) },
            trailingContent = {
                if (isLoggedIn) {
                    OutlinedButton(onClick = {
                        discordName = ""
                        discordToken = ""
                        discordUsername = ""
                    }) {
                        Text(stringResource(R.string.logout))
                    }
                } else {
                    OutlinedButton(onClick = {
                        navController.navigate("settings/discord/login")
                    }) {
                        Text(stringResource(R.string.login))
                    }
                }
            },
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.options),
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_discord_rpc)) },
            checked = discordRPC,
            onCheckedChange = onDiscordRPCChange,
            isEnabled = isLoggedIn,
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.preview),
        )

        RichPresence(song)
    }

    TopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
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
}

@SuppressLint("AutoboxingStateValueProperty", "UseKtx")
@Composable
fun RichPresence(song: Song?) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

    val playbackPosition = remember {
        mutableLongStateOf(playerConnection?.player?.currentPosition ?: 0L)
    }
    val songDuration = song?.song?.duration?.let { it * 1000L } ?: 0L // Ensure duration is in milliseconds

    LaunchedEffect(playerConnection) {
        while (true) {
            playbackPosition.value = playerConnection?.player?.currentPosition ?: 0L
            kotlinx.coroutines.delay(1000L) // Update every second
        }
    }

    val progress = if (playerConnection != null && songDuration > 0L) {
        (playbackPosition.value.toFloat() / songDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp,
        modifier =
        Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Listening to AniTail",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(108.dp),
                ) {
                    AsyncImage(
                        model = song?.song?.thumbnailUrl,
                        contentDescription = null,
                        modifier =
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.TopStart)
                            .run {
                                if (song == null) {
                                    border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(12.dp)
                                    )
                                } else {
                                    this
                                }
                            },
                    )

                    song?.artists?.firstOrNull()?.thumbnailUrl?.let {
                        Box(
                            modifier =
                            Modifier
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    CircleShape
                                )
                                .padding(2.dp)
                                .align(Alignment.BottomEnd),
                        ) {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = song?.song?.title ?: "Song Title",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = song?.artists?.joinToString { it.name } ?: "Artist",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    song?.album?.title?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar with time indicators
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = progress, // Pass progress directly as a value
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playbackPosition.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatTime(songDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                enabled = song != null,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://music.youtube.com/watch?v=${song?.id}".toUri()
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Listen on YouTube Music")
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://discord.gg/H8x3yNbc67".toUri()
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Join our discord")
            }
        }
    }
}

@Composable
fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
