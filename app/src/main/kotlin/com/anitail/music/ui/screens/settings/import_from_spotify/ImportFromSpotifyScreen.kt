package com.anitail.music.ui.screens.settings.import_from_spotify

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.anitail.music.R
import com.anitail.music.ui.screens.settings.import_from_spotify.model.Playlist
import com.anitail.music.viewmodels.ImportFromSpotifyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportFromSpotifyScreen(
    navController: NavController, isMiniPlayerVisible: TopAppBarScrollBehavior
) {
    val importFromSpotifyViewModel: ImportFromSpotifyViewModel = hiltViewModel()
    val importFromSpotifyScreenState = importFromSpotifyViewModel.importFromSpotifyScreenState
    val userPlaylists = importFromSpotifyViewModel.importFromSpotifyScreenState.value.playlists
    val spotifyClientId = rememberSaveable {
        mutableStateOf("")
    }
    val spotifyClientSecret = rememberSaveable {
        mutableStateOf("")
    }
    val spotifyAuthorizationCode = rememberSaveable {
        mutableStateOf("")
    }
    val textFieldPaddingValues = remember {
        PaddingValues(start = 15.dp, end = 15.dp, top = 7.5.dp, bottom = 7.5.dp)
    }
    val localClipBoardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val localUriHandler = LocalUriHandler.current
    val lazyListState = rememberLazyListState()
    val isLikedSongsDestinationDialogShown = rememberSaveable {
        mutableStateOf(false)
    }
    val saveToDefaultLikedSongs: MutableState<Boolean?> = rememberSaveable {
        mutableStateOf(null)
    }
    val logsListState = rememberLazyListState()
    val selectAll = rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(
        lazyListState.canScrollForward, importFromSpotifyScreenState.value.isRequesting
    ) {
        if (importFromSpotifyScreenState.value.isObtainingAccessTokenSuccessful && lazyListState.canScrollForward.not() && importFromSpotifyScreenState.value.reachedEndForPlaylistPagination.not() && importFromSpotifyScreenState.value.isRequesting.not()) {
            importFromSpotifyViewModel.retrieveNextPageOfPlaylists(context)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (importFromSpotifyScreenState.value.accessToken.isNotBlank() && importFromSpotifyScreenState.value.isObtainingAccessTokenSuccessful) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
            ) {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
                Text(
                    text = stringResource(
                        id = R.string.logged_in_as,
                        importFromSpotifyScreenState.value.userName,
                        importFromSpotifyScreenState.value.totalPlaylistsCount
                    ),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 15.dp, bottom = 7.5.dp)
                )
                if (selectAll.value.not()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (importFromSpotifyViewModel.selectedAllPlaylists.value) {
                                    importFromSpotifyViewModel.selectedAllPlaylists.value = false
                                    importFromSpotifyViewModel.selectedPlaylists.clear()
                                    importFromSpotifyViewModel.isLikedSongsSelectedForImport.value =
                                        false
                                    return@clickable
                                }
                                selectAll.value = selectAll.value.not()
                                importFromSpotifyViewModel.selectAllPlaylists(
                                    context, onCompletion = {
                                        selectAll.value = false
                                    })
                            }
                            .animateContentSize()) {
                        Checkbox(
                            checked = importFromSpotifyViewModel.selectedAllPlaylists.value,
                            onCheckedChange = {
                                selectAll.value = it
                            })
                        Spacer(Modifier.width(5.dp))
                        Text(text = stringResource(id = R.string.select_all))
                    }
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, top = 7.5.dp, bottom = 7.5.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    userScrollEnabled = selectAll.value.not()
                ) {
                    item {
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectAll.value) {
                                    return@clickable
                                }
                                importFromSpotifyViewModel.isLikedSongsSelectedForImport.value =
                                    importFromSpotifyViewModel.isLikedSongsSelectedForImport.value.not()
                            }
                            .padding(start = 15.dp, end = 15.dp, top = 7.5.dp, bottom = 7.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(Modifier.width(15.dp))
                                Text(
                                    text = stringResource(id = R.string.liked_songs),
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                )
                            }
                            Checkbox(
                                checked = importFromSpotifyViewModel.isLikedSongsSelectedForImport.value,
                                onCheckedChange = {
                                    if (selectAll.value) {
                                        return@Checkbox
                                    }
                                    importFromSpotifyViewModel.isLikedSongsSelectedForImport.value =
                                        importFromSpotifyViewModel.isLikedSongsSelectedForImport.value.not()
                                })
                        }
                    }
                    items(userPlaylists) { playlist ->
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectAll.value) {
                                    return@clickable
                                }
                                if (importFromSpotifyViewModel.selectedPlaylists.map { it.id }
                                        .contains(
                                            playlist.playlistId
                                        ).not()) {
                                    importFromSpotifyViewModel.selectedPlaylists.add(
                                        Playlist(
                                            name = playlist.playlistName, id = playlist.playlistId
                                        )
                                    )
                                } else {
                                    importFromSpotifyViewModel.selectedPlaylists.removeIf {
                                        it.id == playlist.playlistId
                                    }
                                }
                            }
                            .padding(start = 15.dp, end = 15.dp, top = 7.5.dp, bottom = 7.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                AsyncImage(
                                    model = playlist.images.firstOrNull()?.url ?: "",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(
                                            RoundedCornerShape(10.dp)
                                        )
                                )
                                Spacer(Modifier.width(15.dp))
                                Text(
                                    text = playlist.playlistName
                                )
                            }
                            Checkbox(checked = importFromSpotifyViewModel.selectedPlaylists.map { it.id }
                                .contains(
                                    playlist.playlistId
                                ), onCheckedChange = {
                                if (selectAll.value) {
                                    return@Checkbox
                                }
                                if (importFromSpotifyViewModel.selectedPlaylists.map { it.id }
                                        .contains(
                                            playlist.playlistId
                                        ).not()) {
                                    importFromSpotifyViewModel.selectedPlaylists.add(
                                        Playlist(
                                            name = playlist.playlistName, id = playlist.playlistId
                                        )
                                    )
                                } else {
                                    importFromSpotifyViewModel.selectedPlaylists.removeIf {
                                        it.id == playlist.playlistId
                                    }
                                }
                            })
                        }
                    }
                    if (importFromSpotifyScreenState.value.isRequesting && importFromSpotifyScreenState.value.reachedEndForPlaylistPagination.not()) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(15.dp)
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
            BottomAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp), onClick = {
                        if (selectAll.value) {
                            return@Button
                        }
                        if (importFromSpotifyViewModel.isLikedSongsSelectedForImport.value && saveToDefaultLikedSongs.value == null) {
                            isLikedSongsDestinationDialogShown.value = true
                        } else {
                            importFromSpotifyViewModel.importSelectedItems(
                                saveToDefaultLikedSongs.value, context
                            )
                        }
                    }) {
                    if (selectAll.value) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ButtonDefaults.buttonColors().contentColor,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(text = stringResource(id = R.string.fetching_all_playlists))
                        }
                    } else {
                        Text(text = stringResource(id = R.string.import_selected_items))
                    }
                }
            }
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .align(Alignment.BottomCenter)
                .animateContentSize()
        ) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            val isInstructionExpanded = rememberSaveable {
                mutableStateOf(false)
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isInstructionExpanded.value = isInstructionExpanded.value.not()
                }
                .padding(top = 7.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(id = R.string.instructions_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 15.dp, bottom = 7.5.dp)
                        .fillMaxWidth(0.7f)
                )
                IconButton(onClick = {
                    isInstructionExpanded.value = isInstructionExpanded.value.not()
                }) {
                    Icon(
                        contentDescription = null,
                        imageVector = if (isInstructionExpanded.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore
                    )
                }
            }
            if (isInstructionExpanded.value) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val instructionPadding = remember {
                        PaddingValues(start = 15.dp, bottom = 7.5.dp, end = 7.5.dp)
                    }
                    ClickableText(
                        text = buildAnnotatedString {
                            append(stringResource(id = R.string.instruction_1))
                        },
                        onClick = { offset ->
                            localUriHandler.openUri("https://developer.spotify.com/dashboard/")
                        },
                        style = TextStyle(fontSize = 16.sp, color = LocalContentColor.current),
                        modifier = Modifier.padding(instructionPadding)
                    )
                    SelectionContainer {
                        Text(
                            fontSize = 16.sp,
                            text = stringResource(id = R.string.instruction_2),
                            modifier = Modifier.padding(instructionPadding)
                        )
                    }
                    Text(
                        fontSize = 16.sp,
                        text = stringResource(id = R.string.instruction_3),
                        modifier = Modifier.padding(instructionPadding)
                    )
                    Text(
                        fontSize = 16.sp,
                        text = stringResource(id = R.string.instruction_4),
                        modifier = Modifier.padding(instructionPadding)
                    )
                    Text(
                        fontSize = 16.sp,
                        text = stringResource(id = R.string.instruction_5),
                        modifier = Modifier.padding(instructionPadding)
                    )
                    Text(
                        fontSize = 16.sp,
                        text = stringResource(id = R.string.instruction_6),
                        modifier = Modifier.padding(instructionPadding)
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 15.dp, end = 15.dp, bottom = 7.5.dp),
                        color = LocalContentColor.current.copy(0.1f)
                    )
                    Text(
                        fontSize = 16.sp,
                        text = stringResource(id = R.string.instruction_7),
                        modifier = Modifier.padding(instructionPadding)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp, bottom = 7.5.dp)
            )
            if (importFromSpotifyScreenState.value.error) {
                val isStackTraceVisible = rememberSaveable {
                    mutableStateOf(false)
                }
                Text(
                    text = importFromSpotifyScreenState.value.exception?.message
                        ?: stringResource(id = R.string.error_message),
                    modifier = Modifier.padding(start = 15.dp, end = 15.dp),
                    color = MaterialTheme.colorScheme.error
                )
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        isStackTraceVisible.value = isStackTraceVisible.value.not()
                    }
                    .padding(start = 15.dp, end = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = R.string.stacktrace), fontWeight = FontWeight.Bold)
                        importFromSpotifyScreenState.value.exception?.stackTrace?.joinToString()
                            ?.let {
                                IconButton(onClick = {
                                    localClipBoardManager.setText(AnnotatedString(text = it))
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null
                                    )
                                }
                            }
                    }
                    IconButton(onClick = {
                        isStackTraceVisible.value = isStackTraceVisible.value.not()
                    }) {
                        Icon(
                            imageVector = if (isStackTraceVisible.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                if (isStackTraceVisible.value) {
                    Text(
                        text = importFromSpotifyScreenState.value.exception?.stackTrace?.joinToString()
                            ?: stringResource(id = R.string.error_stacktrace),
                        modifier = Modifier.padding(start = 15.dp, end = 15.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp)
                )
            }
            Text(
                text = stringResource(id = R.string.login_with_spotify_api_credentials),
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 15.dp, bottom = 7.5.dp),
                fontWeight = FontWeight.SemiBold
            )
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(textFieldPaddingValues),
                value = spotifyClientId.value,
                onValueChange = {
                    spotifyClientId.value = it
                },
                label = {
                    Text(text = stringResource(id = R.string.client_id))
                },
                readOnly = importFromSpotifyScreenState.value.isRequesting
            )
            Button(
                onClick = {
                    localUriHandler.openUri("https://accounts.spotify.com/authorize?client_id=${spotifyClientId.value}&response_type=code&redirect_uri=http://127.0.0.1:8888&scope=user-library-read%20playlist-read-private%20playlist-read-collaborative")                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp)
            ) {
                Text(text = stringResource(id = R.string.authorize_and_continue))
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp, top = 7.5.dp, bottom = 7.5.dp)
            )
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(textFieldPaddingValues),
                value = spotifyClientSecret.value,
                onValueChange = {
                    spotifyClientSecret.value = it
                },
                label = {
                    Text(text = stringResource(id = R.string.client_secret))
                },
                readOnly = importFromSpotifyScreenState.value.isRequesting
            )
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(textFieldPaddingValues),
                value = spotifyAuthorizationCode.value,
                onValueChange = {
                    spotifyAuthorizationCode.value =
                        it.substringAfter("http://127.0.0.1:8888/?code=").trim()
                },
                label = {
                    Text(text = stringResource(id = R.string.authorization_code))
                },
                readOnly = importFromSpotifyScreenState.value.isRequesting
            )
            if (importFromSpotifyScreenState.value.isRequesting.not()) {
                Button(
                    onClick = {
                        importFromSpotifyViewModel.spotifyLoginAndFetchPlaylists(
                            clientId = spotifyClientId.value.trim(),
                            clientSecret = spotifyClientSecret.value.trim(),
                            authorizationCode = spotifyAuthorizationCode.value.trim(),
                            context
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, bottom = 15.dp, end = 15.dp)
                ) {
                    Text(text = stringResource(id = R.string.authenticate))
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, bottom = 30.dp, end = 15.dp, top = 7.5.dp)
                )
            }
        }
    }
    if (importFromSpotifyViewModel.isImportingInProgress.value) {
        Scaffold(topBar = {
            Column(modifier = Modifier
                .clickable { }
                .fillMaxWidth()
                .padding(15.dp)
                .windowInsetsPadding(WindowInsets.statusBars)) {
                Text(stringResource(id = R.string.import_in_progress), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(id = R.string.import_in_progress_message),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(5.dp))
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
            }
        }) {
            Box(modifier = Modifier
                .padding(it)
                .clickable { }
                .fillMaxSize()
                .padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
                contentAlignment = Alignment.BottomCenter) {
                LazyColumn(
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize(),
                    state = logsListState
                ) {
                    items(importFromSpotifyViewModel.importLogs) {
                        Text(text = it)
                    }
                }
            }
        }
    }
    if (isLikedSongsDestinationDialogShown.value) {
        BasicAlertDialog(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .clip(
                    RoundedCornerShape(15.dp)
                )
                .background(AlertDialogDefaults.containerColor), onDismissRequest = {
                isLikedSongsDestinationDialogShown.value = false
            }) {
            Column(modifier = Modifier.padding(15.dp)) {
                Text(
                    text = stringResource(id = R.string.choose_liked_songs_destination),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text(text = stringResource(id = R.string.liked_songs_destination_message))
                Spacer(Modifier.height(15.dp))
                Button(onClick = {
                    saveToDefaultLikedSongs.value = false
                    importFromSpotifyViewModel.importSelectedItems(
                        saveToDefaultLikedSongs.value, context
                    )
                    isLikedSongsDestinationDialogShown.value = false
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(id = R.string.new_playlist_liked_songs))
                }
                Spacer(Modifier.height(5.dp))
                Button(onClick = {
                    saveToDefaultLikedSongs.value = true
                    importFromSpotifyViewModel.importSelectedItems(
                        saveToDefaultLikedSongs.value, context
                    )
                    isLikedSongsDestinationDialogShown.value = false
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(id = R.string.default_liked_songs))
                }
            }
        }
    }
    LaunchedEffect(importFromSpotifyViewModel.isImportingCompleted.value) {
        if (importFromSpotifyViewModel.isImportingCompleted.value) {
            Toast.makeText(context, context.getString(R.string.import_succeeded), Toast.LENGTH_LONG).show()
            navController.navigateUp()
        }
    }
    BackHandler {
        if (importFromSpotifyViewModel.isImportingInProgress.value) {
            Toast.makeText(
                context,
                context.getString(R.string.import_in_progress_warning),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            navController.navigateUp()
        }
    }
    LaunchedEffect(logsListState.canScrollForward) {
        if (logsListState.canScrollForward) {
            logsListState.animateScrollToItem(logsListState.layoutInfo.totalItemsCount - 1)
        }
    }
}

