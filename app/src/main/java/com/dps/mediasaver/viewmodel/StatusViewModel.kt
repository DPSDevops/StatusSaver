package com.dps.mediasaver.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dps.mediasaver.model.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class StatusViewModel : ViewModel() {
    private val TAG = "StatusViewModel"
    private val _statusItems = MutableStateFlow<List<StatusItem>>(emptyList())
    val statusItems: StateFlow<List<StatusItem>> = _statusItems

    // Selection state
    private val _selectedItems = MutableStateFlow<Set<StatusItem>>(emptySet())
    val selectedItems: StateFlow<Set<StatusItem>> = _selectedItems
    
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    fun toggleSelection(item: StatusItem) {
        val currentSelection = _selectedItems.value.toMutableSet()
        if (currentSelection.contains(item)) {
            currentSelection.remove(item)
            if (currentSelection.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            currentSelection.add(item)
            _isSelectionMode.value = true
        }
        _selectedItems.value = currentSelection
    }

    fun selectAll() {
        _selectedItems.value = _statusItems.value.toSet()
        _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    private fun getWhatsAppPaths(context: Context): List<String> {
        val paths = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ paths
            val baseDir = Environment.getExternalStorageDirectory().absolutePath
            paths.add("$baseDir/Android/media/com.whatsapp/WhatsApp/Media/.Statuses")
            paths.add("$baseDir/WhatsApp/Media/.Statuses")
            paths.add("$baseDir/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Status")
            // Business paths
            paths.add("$baseDir/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses")
            paths.add("$baseDir/WhatsApp Business/Media/.Statuses")
        } else {
            // Legacy paths
            val baseDir = Environment.getExternalStorageDirectory().absolutePath
            paths.add("$baseDir/WhatsApp/Media/.Statuses")
            paths.add("$baseDir/WhatsApp Business/Media/.Statuses")
        }
        
        Log.d(TAG, "Generated paths: $paths")
        return paths
    }

    fun loadStatuses(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting status load...")
            
            // Check if we have necessary permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Log.d(TAG, "Missing MANAGE_EXTERNAL_STORAGE permission")
                _statusItems.emit(emptyList())
                return@launch
            }
            
            var foundStatuses = false
            
            for (path in getWhatsAppPaths(context)) {
                val folder = File(path)
                Log.d(TAG, "Checking path: $path")
                Log.d(TAG, "Folder exists: ${folder.exists()}")
                Log.d(TAG, "Is directory: ${folder.isDirectory}")
                Log.d(TAG, "Can read: ${folder.canRead()}")
                
                if (folder.exists() && folder.isDirectory && folder.canRead()) {
                    val files = folder.listFiles()
                    Log.d(TAG, "Number of files found in $path: ${files?.size ?: 0}")
                    
                    if (files != null && files.isNotEmpty()) {
                        val statusFiles = files.filter {
                            val isValid = (it.name.endsWith(".jpg") || it.name.endsWith(".mp4")) && it.canRead()
                            Log.d(TAG, "File: ${it.name}, isValid: $isValid, canRead: ${it.canRead()}")
                            isValid
                        }.map { file ->
                            StatusItem(
                                file = file,
                                uri = Uri.fromFile(file),
                                isVideo = file.name.endsWith(".mp4"),
                                timestamp = file.lastModified()
                            )
                        }.sortedByDescending { it.timestamp }

                        Log.d(TAG, "Number of valid status files: ${statusFiles.size}")
                        if (statusFiles.isNotEmpty()) {
                            foundStatuses = true
                            _statusItems.emit(statusFiles)
                            break
                        }
                    }
                }
            }
            
            if (!foundStatuses) {
                Log.d(TAG, "No status files found in any location")
                _statusItems.emit(emptyList())
            }
        }
    }
} 