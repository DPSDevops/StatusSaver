package com.dps.mediasaver

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.mediasaver.model.StatusItem
import com.dps.mediasaver.ui.components.StatusItemComponent
import com.dps.mediasaver.ui.screens.PermissionScreen
import com.dps.mediasaver.ui.screens.StatusPreviewScreen
import com.dps.mediasaver.ui.theme.StatusSaverTheme
import com.dps.mediasaver.viewmodel.StatusViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class StatusFilter {
    ALL, IMAGES, VIDEOS, RECENT, OLDER
}

enum class GridSize(val columns: Int) {
    SMALL(4), MEDIUM(3), LARGE(2)
}

@OptIn(ExperimentalAnimationApi::class)
class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val viewModel: StatusViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.d(TAG, "onCreate: Starting app")
        
        setContent {
            StatusSaverTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var showManageStorageDialog by remember { mutableStateOf(false) }
                var selectedStatus by remember { mutableStateOf<StatusItem?>(null) }
                var currentFilter by remember { mutableStateOf(StatusFilter.ALL) }
                var currentGridSize by remember { mutableStateOf(GridSize.MEDIUM) }
                var showFilterSheet by remember { mutableStateOf(false) }

                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }

                val permissionsState = rememberMultiplePermissionsState(permissions)
                val statusItems by viewModel.statusItems.collectAsState()
                val filteredItems = remember(statusItems, currentFilter) {
                    when (currentFilter) {
                        StatusFilter.ALL -> statusItems
                        StatusFilter.IMAGES -> statusItems.filter { !it.isVideo }
                        StatusFilter.VIDEOS -> statusItems.filter { it.isVideo }
                        StatusFilter.RECENT -> statusItems.filter { 
                            it.timestamp > System.currentTimeMillis() - (24 * 60 * 60 * 1000) 
                        }
                        StatusFilter.OLDER -> statusItems.filter { 
                            it.timestamp <= System.currentTimeMillis() - (24 * 60 * 60 * 1000) 
                        }
                    }
                }

                // Handle initial permission check
                LaunchedEffect(Unit) {
                    if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                             !Environment.isExternalStorageManager()) {
                        showManageStorageDialog = true
                    } else {
                        viewModel.loadStatuses(context)
                    }
                }

                // Handle permission state changes
                LaunchedEffect(permissionsState.allPermissionsGranted) {
                    if (permissionsState.allPermissionsGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                            !Environment.isExternalStorageManager()) {
                            showManageStorageDialog = true
                        } else {
                            viewModel.loadStatuses(context)
                        }
                    }
                }

                if (selectedStatus != null) {
                    StatusPreviewScreen(
                        statusItem = selectedStatus!!,
                        onBackPressed = { selectedStatus = null }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            Column {
                                TopAppBar(
                                    title = { Text("Status Saver") },
                                    actions = {
                                        val isSelectionMode by viewModel.isSelectionMode.collectAsState()
                                        val selectedItems by viewModel.selectedItems.collectAsState()
                                        
                                        if (isSelectionMode) {
                                            Text(
                                                text = "${selectedItems.size} selected",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            IconButton(
                                                onClick = { viewModel.selectAll() }
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_select_all),
                                                    contentDescription = "Select All"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        selectedItems.forEach { status ->
                                                            // Create directory in Pictures directory
                                                            val saveDir = File(
                                                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                                                "Status Saver"
                                                            ).apply { mkdirs() }
                                                            
                                                            val timestamp = System.currentTimeMillis()
                                                            val extension = if (status.isVideo) ".mp4" else ".jpg"
                                                            val fileName = "Status_$timestamp$extension"
                                                            val destFile = File(saveDir, fileName)
                                                            
                                                            try {
                                                                status.file.inputStream().use { input ->
                                                                    FileOutputStream(destFile).use { output ->
                                                                        input.copyTo(output)
                                                                    }
                                                                }
                                                                
                                                                // Notify media scanner
                                                                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                                                val fileUri = Uri.fromFile(destFile)
                                                                mediaScanIntent.data = fileUri
                                                                context.sendBroadcast(mediaScanIntent)
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                                Toast.makeText(
                                                                    context,
                                                                    "Failed to save ${if (status.isVideo) "video" else "image"}: ${e.message}",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                        Toast.makeText(
                                                            context,
                                                            "Saved ${selectedItems.size} items",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        viewModel.clearSelection()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_download),
                                                    contentDescription = "Download Selected"
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.clearSelection() }
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_close),
                                                    contentDescription = "Clear Selection"
                                                )
                                            }
                                        } else {
                                            IconButton(onClick = { showFilterSheet = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Menu,
                                                    contentDescription = "Grid Size"
                                                )
                                            }
                                            IconButton(onClick = { viewModel.loadStatuses(context) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Refresh"
                                                )
                                            }
                                        }
                                    }
                                )
                                AnimatedVisibility(
                                    visible = true,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ScrollableTabRow(
                                        selectedTabIndex = currentFilter.ordinal,
                                        modifier = Modifier.fillMaxWidth(),
                                        edgePadding = 8.dp
                                    ) {
                                        StatusFilter.values().forEach { filter ->
                                            Tab(
                                                selected = currentFilter == filter,
                                                onClick = { 
                                                    currentFilter = filter 
                                                },
                                                text = {
                                                    Text(
                                                        text = filter.name.lowercase()
                                                            .replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        if (showManageStorageDialog) {
                            AlertDialog(
                                onDismissRequest = { 
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                                        Environment.isExternalStorageManager()) {
                                        showManageStorageDialog = false
                                        viewModel.loadStatuses(context)
                                    }
                                },
                                title = { Text("Additional Permission Required") },
                                text = { 
                                    Column {
                                        Text("This app needs access to manage external storage to read media files.")
                                        Text("Please grant 'All files access' permission in Settings.")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Fallback for devices that don't support the direct intent
                                                try {
                                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Failed to open settings", e)
                                                    Toast.makeText(context, "Please grant 'All files access' permission from Settings manually", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Open Settings")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showManageStorageDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showFilterSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showFilterSheet = false }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Grid Size",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        GridSize.values().forEach { size ->
                                            FilterChip(
                                                selected = currentGridSize == size,
                                                onClick = { currentGridSize = size },
                                                label = { 
                                                    Text(size.name.lowercase().replaceFirstChar { it.uppercase() })
                                                }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        Box(modifier = Modifier.padding(padding)) {
                            if (!permissionsState.allPermissionsGranted) {
                                PermissionScreen(
                                    onRequestPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            showManageStorageDialog = true
                                        } else {
                                            permissionsState.launchMultiplePermissionRequest()
                                        }
                                    }
                                )
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                                     !Environment.isExternalStorageManager()) {
                                PermissionScreen(
                                    onRequestPermission = {
                                        showManageStorageDialog = true
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    viewModel.loadStatuses(context)
                                }
                                if (filteredItems.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        // Empty state illustration
                                        Image(
                                            painter = painterResource(id = R.drawable.ill_empty),
                                            contentDescription = null,
                                            modifier = Modifier.size(180.dp)
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Text(
                                            text = when (currentFilter) {
                                                StatusFilter.ALL -> "No updates found"
                                                StatusFilter.IMAGES -> "No images found"
                                                StatusFilter.VIDEOS -> "No videos found"
                                                StatusFilter.RECENT -> "No recent updates found"
                                                StatusFilter.OLDER -> "No older updates found"
                                            },
                                            style = MaterialTheme.typography.headlineSmall,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Make sure you:\n" +
                                                "1. Have the messaging app installed\n" +
                                                "2. Have viewed some updates recently\n" +
                                                "3. Have granted all permissions",
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(
                                            onClick = { viewModel.loadStatuses(context) }
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Refresh")
                                        }
                                    }
                                } else {
                                    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
                                    val selectedItems by viewModel.selectedItems.collectAsState()
                                    
                                    AnimatedContent(
                                        targetState = Pair(currentFilter, currentGridSize),
                                        transitionSpec = {
                                            fadeIn() + slideInHorizontally() with 
                                            fadeOut() + slideOutHorizontally()
                                        }
                                    ) { (filter, gridSize) ->
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(gridSize.columns),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(
                                                items = filteredItems,
                                                key = { it.file.absolutePath }
                                            ) { status ->
                                                StatusItemComponent(
                                                    statusItem = status,
                                                    onClick = { 
                                                        if (isSelectionMode) {
                                                            viewModel.toggleSelection(it)
                                                        } else {
                                                            selectedStatus = it
                                                        }
                                                    },
                                                    onLongClick = { 
                                                        if (!isSelectionMode) {
                                                            viewModel.toggleSelection(it)
                                                        }
                                                    },
                                                    isSelected = selectedItems.contains(status),
                                                    isSelectionMode = isSelectionMode
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            viewModel.loadStatuses(this)
        }
    }
}