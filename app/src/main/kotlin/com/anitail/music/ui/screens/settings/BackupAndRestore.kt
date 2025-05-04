package com.anitail.music.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.anitail.music.LocalPlayerAwareWindowInsets
import com.anitail.music.R
import com.anitail.music.db.entities.Song
import com.anitail.music.ui.component.IconButton
import com.anitail.music.ui.menu.AddToPlaylistDialogOnline
import com.anitail.music.ui.menu.LoadingScreen
import com.anitail.music.ui.utils.backToMain
import com.anitail.music.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable {
        mutableStateOf(false)
    }

    var isProgressStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var progressPercentage by rememberSaveable {
        mutableIntStateOf(0)
    }
    
    // Estado para seguimiento de carga de backup
    var uploadStatus by remember { mutableStateOf<UploadStatus?>(null) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri)
                // Iniciar carga a filebin.net
                coroutineScope.launch {
                    uploadStatus = UploadStatus.Uploading
                    uploadProgress = 0f
                    val fileUrl = uploadBackupToFilebin(context, uri) { progress ->
                        uploadProgress = progress
                    }
                    uploadStatus = if (fileUrl != null) {
                        UploadStatus.Success(fileUrl)
                    } else {
                        UploadStatus.Failure
                    }
                }
            }
        }
        
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restore(context, uri)
            }
        }

    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val result = viewModel.importPlaylistFromCsv(context, uri)
            importedSongs.clear()
            importedSongs.addAll(result)

            if (importedSongs.isNotEmpty()) {
                showChoosePlaylistDialogOnline = true
            }
        }

    val importM3uLauncherOnline = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = viewModel.loadM3UOnline(context, uri)
        importedSongs.clear()
        importedSongs.addAll(result)

        if (importedSongs.isNotEmpty()) {
            showChoosePlaylistDialogOnline = true
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.backup_restore)) },
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
        },
        scrollBehavior = scrollBehavior
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Sección de información
        InfoSection()

        // Sección de acciones principales
        ActionSection(
            onBackupClick = {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${
                        LocalDateTime.now().format(formatter)
                    }.backup"
                )
            },
            onRestoreClick = {
                restoreLauncher.launch(arrayOf("application/octet-stream"))
            },
            onImportOnlineClick = {
                importM3uLauncherOnline.launch(arrayOf("audio/*"))
            },
            onImportCsvClick = {
                importPlaylistFromCsv.launch(arrayOf("text/csv"))
            }
        )
        
        // Sección de estado de carga
        UploadStatusSection(
            uploadStatus = uploadStatus,
            uploadProgress = uploadProgress,
            onCopyClick = {
                if (uploadStatus is UploadStatus.Success) {
                    copyToClipboard(context, (uploadStatus as UploadStatus.Success).fileUrl)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        initialTextFieldValue = importedTitle,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { newVal -> isProgressStarted = newVal },
        onPercentageChange = { newPercentage -> progressPercentage = newPercentage }
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    LoadingScreen(
        isVisible = isProgressStarted,
        value = progressPercentage,
    )
}

@Composable
private fun InfoSection() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.backup_restore),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = stringResource(R.string.backup_info_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionSection(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportOnlineClick: () -> Unit,
    onImportCsvClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionButton(
            icon = painterResource(R.drawable.backup),
            title = stringResource(R.string.backup),
            description = stringResource(R.string.backup_description),
            isPrimary = true,
            onClick = onBackupClick
        )

        ActionButton(
            icon = painterResource(R.drawable.restore),
            title = stringResource(R.string.restore),
            description = stringResource(R.string.restore_description),
            isPrimary = false,
            onClick = onRestoreClick
        )

        ActionButton(
            icon = painterResource(id = R.drawable.add),
            title = stringResource(R.string.import_online),
            description = stringResource(R.string.import_online_description),
            isPrimary = false,
            onClick = onImportOnlineClick
        )

        ActionButton(
            icon = painterResource(id = R.drawable.add),
            title = stringResource(R.string.import_csv),
            description = stringResource(R.string.import_csv_description),
            isPrimary = false,
            onClick = onImportCsvClick
        )
    }
}

@Composable
private fun ActionButton(
    icon: Painter,
    title: String,
    description: String,
    isPrimary: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isPrimary)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f)
                )
            }

            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun UploadStatusSection(
    uploadStatus: UploadStatus?,
    uploadProgress: Float = 0f,
    onCopyClick: () -> Unit
) {
    when (uploadStatus) {
        is UploadStatus.Uploading -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Text(
                        text = stringResource(R.string.uploading_backup),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = stringResource(
                            R.string.upload_progress_percentage, 
                            (uploadProgress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is UploadStatus.Success -> {
            SuccessCard(fileUrl = uploadStatus.fileUrl, onCopyClick = onCopyClick)
        }

        is UploadStatus.Failure -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.upload_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        null -> { /* No status to show */ }
    }
}

@Composable
private fun SuccessCard(fileUrl: String, onCopyClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.backup_link_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.backup_link_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = fileUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = onCopyClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_link))
                    }
                }
            }
        }
    }
}

suspend fun uploadBackupToFilebin(
    context: Context,
    uri: Uri,
    progressCallback: (Float) -> Unit = {}
): String? {
    return withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.backup")

        try {
            // Copiar archivo a almacenamiento temporal con seguimiento de progreso
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileSize = inputStream.available().toFloat()
                var totalBytesRead = 0L

                tempFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        progressCallback(totalBytesRead / fileSize * 0.5f) // Primera mitad es copia de archivo
                    }
                }
            } ?: return@withContext null

            // Generar un bin ID aleatorio para filebin.net
            val binId = UUID.randomUUID().toString().substring(0, 8)

            // Crear RequestBody con monitoreo de progreso
            val fileRequestBody = object : RequestBody() {
                override fun contentType(): MediaType? =
                    "application/octet-stream".toMediaTypeOrNull()

                override fun contentLength(): Long = tempFile.length()

                override fun writeTo(sink: BufferedSink) {
                    tempFile.inputStream().use { input ->
                        val fileSize = tempFile.length().toFloat()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            sink.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val uploadProgress = 0.5f + (totalBytesRead / fileSize * 0.5f)
                            progressCallback(uploadProgress)
                        }
                    }
                }
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val fileName = tempFile.name
            val request = Request.Builder()
                .url("https://filebin.net/$binId/$fileName")
                .put(fileRequestBody)
                .build()

            val response = client.newCall(request).execute()

            // Limpiar archivo temporal
            if (tempFile.exists()) {
                tempFile.delete()
            }

            if (!response.isSuccessful) {
                return@withContext null
            }

            // URL predecible de filebin.net
            return@withContext "https://filebin.net/$binId/$fileName"

        } catch (e: Exception) {
            // Limpiar archivo temporal en caso de excepción
            if (tempFile.exists()) {
                tempFile.delete()
            }

            return@withContext null
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Backup URL", text)
    clipboard.setPrimaryClip(clip)
}

sealed class UploadStatus {
    data object Uploading : UploadStatus()
    data class Success(val fileUrl: String) : UploadStatus()
    data object Failure : UploadStatus()
}
