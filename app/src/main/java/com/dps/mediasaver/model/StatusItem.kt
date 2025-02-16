package com.dps.mediasaver.model

import android.net.Uri
import java.io.File

data class StatusItem(
    val file: File,
    val uri: Uri,
    val isVideo: Boolean,
    val timestamp: Long
) 