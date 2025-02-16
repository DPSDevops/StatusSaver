package com.dps.mediasaver.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.dps.mediasaver.R
import com.dps.mediasaver.model.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class SaveStatus {
    object None : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val file: File) : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPreviewScreen(
    statusItem: StatusItem,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var saveStatus by remember { mutableStateOf<SaveStatus>(SaveStatus.None) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(
                        onClick = {
                            if (saveStatus !is SaveStatus.Saving) {
                                val fileToShare = when (val status = saveStatus) {
                                    is SaveStatus.Success -> status.file
                                    else -> statusItem.file
                                }
                                
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    fileToShare
                                )
                                
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (statusItem.isVideo) "video/*" else "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Status"))
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Share",
                            tint = if (saveStatus is SaveStatus.Saving) 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            if (saveStatus !is SaveStatus.Saving) {
                                scope.launch {
                                    saveStatus = SaveStatus.Saving
                                    try {
                                        val savedFile = withContext(Dispatchers.IO) {
                                            val saveDir = File(context.getExternalFilesDir(null), "Saved Status")
                                            if (!saveDir.exists()) {
                                                saveDir.mkdirs()
                                            }
                                            
                                            val destFile = File(saveDir, statusItem.file.name)
                                            statusItem.file.inputStream().use { input ->
                                                FileOutputStream(destFile).use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            destFile
                                        }
                                        saveStatus = SaveStatus.Success(savedFile)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        saveStatus = SaveStatus.Error("Failed to save status: ${e.message}")
                                    }
                                }
                            }
                        }
                    ) {
                        if (saveStatus is SaveStatus.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Save"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (statusItem.isVideo) {
                VideoPlayer(uri = statusItem.uri)
            } else {
                AsyncImage(
                    model = statusItem.uri,
                    contentDescription = "Status Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            AnimatedVisibility(
                visible = saveStatus !is SaveStatus.None,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    action = {
                        TextButton(
                            onClick = { saveStatus = SaveStatus.None }
                        ) {
                            Text("OK")
                        }
                    },
                    containerColor = when (saveStatus) {
                        is SaveStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                        is SaveStatus.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (saveStatus) {
                            is SaveStatus.Saving -> "Saving status..."
                            is SaveStatus.Success -> "Status saved successfully!"
                            is SaveStatus.Error -> (saveStatus as SaveStatus.Error).message
                            else -> ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
} 