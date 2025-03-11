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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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

                AnimatedContent(
                    targetState = selectedStatus,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) with 
                        fadeOut(animationSpec = tween(300))
                    }
                ) { targetState ->
                    if (targetState != null) {
                        StatusPreviewScreen(
                            statusItem = targetState,
                            onBackPressed = { selectedStatus = null }
                        )
                    } else {
                        Scaffold(
                            topBar = {
                                Column {
                                    TopAppBar(
                                        title = { 
                                            Text(
                                                "Status Saver",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) 
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        actions = {
                                            val isSelectionMode by viewModel.isSelectionMode.collectAsState()
                                            val selectedItems by viewModel.selectedItems.collectAsState()
                                            
                                            AnimatedContent(
                                                targetState = isSelectionMode,
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(300)) with 
                                                    fadeOut(animationSpec = tween(300))
                                                }
                                            ) { inSelectionMode ->
                                                if (inSelectionMode) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${selectedItems.size} selected",
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        IconButton(
                                                            onClick = { viewModel.selectAll() }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_select_all),
                                                                contentDescription = "Select All"
                                                            )
                                                        }
                                                        
                                                        // Download button with visual feedback
                                                        val downloadInteraction = remember { MutableInteractionSource() }
                                                        val isDownloadPressed by downloadInteraction.collectIsPressedAsState()
                                                        val downloadScale by animateFloatAsState(
                                                            targetValue = if (isDownloadPressed) 0.9f else 1f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                        
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
                                                            },
                                                            modifier = Modifier.scale(downloadScale),
                                                            interactionSource = downloadInteraction
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_download),
                                                                contentDescription = "Download Selected",
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        
                                                        IconButton(
                                                            onClick = { viewModel.clearSelection() }
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(id = R.drawable.ic_close),
                                                                contentDescription = "Clear Selection"
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Row {
                                                        IconButton(onClick = { showFilterSheet = true }) {
                                                            Icon(
                                                                imageVector = Icons.Default.Menu,
                                                                contentDescription = "Grid Size"
                                                            )
                                                        }
                                                        
                                                        // Refresh button with animation
                                                        val refreshInteraction = remember { MutableInteractionSource() }
                                                        val isRefreshPressed by refreshInteraction.collectIsPressedAsState()
                                                        val rotation by animateFloatAsState(
                                                            targetValue = if (isRefreshPressed) 180f else 0f,
                                                            animationSpec = tween(300)
                                                        )
                                                        
                                                        IconButton(
                                                            onClick = { viewModel.loadStatuses(context) },
                                                            interactionSource = refreshInteraction
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "Refresh",
                                                                modifier = Modifier.graphicsLayer {
                                                                    rotationZ = rotation
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    
                                    // Enhanced tab row with animations
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 1.dp
                                        ) {
                                            ScrollableTabRow(
                                                selectedTabIndex = currentFilter.ordinal,
                                                modifier = Modifier.fillMaxWidth(),
                                                edgePadding = 8.dp,
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                indicator = { tabPositions ->
                                                    TabRowDefaults.Indicator(
                                                        modifier = Modifier
                                                            .tabIndicatorOffset(tabPositions[currentFilter.ordinal]),
                                                        height = 3.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
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
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontWeight = if (currentFilter == filter) 
                                                                                    FontWeight.Bold 
                                                                                 else 
                                                                                    FontWeight.Normal
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
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
                                    title = { 
                                        Text(
                                            "Additional Permission Required",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    },
                                    text = { 
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("This app needs access to manage external storage to read media files.")
                                            Text("Please grant 'All files access' permission in Settings.")
                                        }
                                    },
                                    confirmButton = {
                                        Button(
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
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Open Settings")
                                        }
                                    },
                                    dismissButton = {
                                        OutlinedButton(
                                            onClick = { showManageStorageDialog = false },
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

                            if (showFilterSheet) {
                                ModalBottomSheet(
                                    onDismissRequest = { showFilterSheet = false },
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Grid Size",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
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
                                                        Text(
                                                            size.name.lowercase().replaceFirstChar { it.uppercase() },
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = if (currentGridSize == size) 
                                                                                FontWeight.Bold 
                                                                             else 
                                                                                FontWeight.Normal
                                                            )
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
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
                                            // Simple empty state without animation
                                            Image(
                                                painter = painterResource(id = R.drawable.ill_empty),
                                                contentDescription = null,
                                                modifier = Modifier.size(200.dp)
                                            )

                                            Spacer(modifier = Modifier.height(32.dp))

                                            Text(
                                                text = when (currentFilter) {
                                                    StatusFilter.ALL -> "No statuses found"
                                                    StatusFilter.IMAGES -> "No images found"
                                                    StatusFilter.VIDEOS -> "No videos found"
                                                    StatusFilter.RECENT -> "No recent statuses found"
                                                    StatusFilter.OLDER -> "No older statuses found"
                                                },
                                                style = MaterialTheme.typography.headlineSmall,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                Text(
                                                    text = "Make sure you:\n" +
                                                        "1. Have the messaging app installed\n" +
                                                        "2. Have viewed some statuses recently\n" +
                                                        "3. Have granted all permissions",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(32.dp))
                                            
                                            Button(
                                                onClick = { viewModel.loadStatuses(context) },
                                                shape = RoundedCornerShape(24.dp),
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh, 
                                                    contentDescription = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Refresh",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        val isSelectionMode by viewModel.isSelectionMode.collectAsState()
                                        val selectedItems by viewModel.selectedItems.collectAsState()
                                        
                                        AnimatedContent(
                                            targetState = Triple(currentFilter, currentGridSize, filteredItems.size),
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(300)) + 
                                                slideInHorizontally(animationSpec = tween(300)) with 
                                                fadeOut(animationSpec = tween(300)) + 
                                                slideOutHorizontally(animationSpec = tween(300))
                                            }
                                        ) { (filter, gridSize, itemCount) ->
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(gridSize.columns),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.background),
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
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            viewModel.loadStatuses(this)
        }
    }
}