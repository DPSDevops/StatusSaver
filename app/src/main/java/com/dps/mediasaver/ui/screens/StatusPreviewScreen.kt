package com.dps.mediasaver.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri as AndroidUri
import android.content.ContentUris

sealed class SaveStatus {
    object None : SaveStatus()
    object Saving : SaveStatus()
    data class Success(val file: File, val uri: Uri? = null) : SaveStatus()
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
    var showOpenDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Dialog to open saved media with improved UI
    if (showOpenDialog) {
        AlertDialog(
            onDismissRequest = { showOpenDialog = false },
            title = { 
                Text(
                    "Status Saved Successfully!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Text(
                        text = "Would you like to open the saved ${if (statusItem.isVideo) "video" else "image"}?",
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOpenDialog = false
                        (saveStatus as? SaveStatus.Success)?.uri?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, if (statusItem.isVideo) "video/*" else "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showOpenDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    // Share button with bounce animation
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    
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
                        },
                        interactionSource = interactionSource
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.scale(scale),
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
                                            // Create directory in Pictures directory for better gallery integration
                                            val saveDir = File(
                                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                                "Status Saver"
                                            ).apply {
                                                mkdirs()
                                            }
                                            
                                            val timestamp = System.currentTimeMillis()
                                            val extension = if (statusItem.isVideo) ".mp4" else ".jpg"
                                            val fileName = "Status_$timestamp$extension"
                                            
                                            val destFile = File(saveDir, fileName)
                                            
                                            statusItem.file.inputStream().use { input ->
                                                FileOutputStream(destFile).use { output ->
                                                    val buffer = ByteArray(8 * 1024)
                                                    var bytes = input.read(buffer)
                                                    while (bytes >= 0) {
                                                        output.write(buffer, 0, bytes)
                                                        bytes = input.read(buffer)
                                                    }
                                                    output.flush()
                                                }
                                            }
                                            destFile
                                        }
                                        
                                        // Notify media scanner and get content URI
                                        val contentUri = withContext(Dispatchers.IO) {
                                            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                            val fileUri = AndroidUri.fromFile(savedFile)
                                            mediaScanIntent.data = fileUri
                                            context.sendBroadcast(mediaScanIntent)
                                            
                                            // Get content URI for the saved file
                                            val projection = arrayOf(MediaStore.MediaColumns._ID)
                                            val selection = MediaStore.MediaColumns.DATA + "=?"
                                            val selectionArgs = arrayOf(savedFile.absolutePath)
                                            val contentUri = if (statusItem.isVideo) {
                                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                            } else {
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                            }
                                            
                                            context.contentResolver.query(
                                                contentUri,
                                                projection,
                                                selection,
                                                selectionArgs,
                                                null
                                            )?.use { cursor ->
                                                if (cursor.moveToFirst()) {
                                                    val id = cursor.getLong(0)
                                                    ContentUris.withAppendedId(contentUri, id)
                                                } else null
                                            }
                                        }
                                        
                                        saveStatus = SaveStatus.Success(savedFile, contentUri)
                                        showOpenDialog = true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        saveStatus = SaveStatus.Error("Failed to save status: ${e.message}")
                                    }
                                }
                            }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 12.dp
                        )
                    ) {
                        if (saveStatus is SaveStatus.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = "Save",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = statusItem.uri,
                        contentDescription = "Status Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Enhanced status notification with animations
            AnimatedVisibility(
                visible = saveStatus is SaveStatus.Success || saveStatus is SaveStatus.Error,
                enter = fadeIn(animationSpec = tween(300)) + 
                       slideInVertically(
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioMediumBouncy,
                               stiffness = Spring.StiffnessLow
                           ),
                           initialOffsetY = { it }
                       ),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Snackbar(
                    action = {
                        TextButton(
                            onClick = { saveStatus = SaveStatus.None }
                        ) {
                            Text(
                                "DISMISS",
                                color = when (saveStatus) {
                                    is SaveStatus.Success -> MaterialTheme.colorScheme.primary
                                    is SaveStatus.Error -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = when (saveStatus) {
                        is SaveStatus.Success -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        is SaveStatus.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    },
                    contentColor = when (saveStatus) {
                        is SaveStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                        is SaveStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = if (saveStatus is SaveStatus.Success) 
                                         painterResource(id = R.drawable.ic_select_all) // Using existing icon as fallback
                                      else 
                                         painterResource(id = R.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Text(
                            text = when (saveStatus) {
                                is SaveStatus.Success -> "Status saved successfully!"
                                is SaveStatus.Error -> (saveStatus as SaveStatus.Error).message
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Loading indicator when saving
            AnimatedVisibility(
                visible = saveStatus is SaveStatus.Saving,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .size(100.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Saving...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
                useController = true
                controllerShowTimeoutMs = 1500
                showController()
            }
        },
        modifier = Modifier.fillMaxSize()
    )
} 