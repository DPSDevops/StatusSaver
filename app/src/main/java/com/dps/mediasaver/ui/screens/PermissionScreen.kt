package com.dps.mediasaver.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dps.mediasaver.R
import com.dps.mediasaver.ui.theme.Accent1

@Composable
fun PermissionScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated entry for the storage illustration
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(600)) + 
                       slideInVertically(
                           animationSpec = spring(
                               dampingRatio = Spring.DampingRatioLowBouncy,
                               stiffness = Spring.StiffnessLow
                           ),
                           initialOffsetY = { -200 }
                       ),
                exit = fadeOut()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ill_storage),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // Title
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Description
            Text(
                text = "To save WhatsApp statuses, we need access to your device storage:",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Permission details with improved visual appeal
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PermissionItem(
                        iconResId = R.drawable.ic_photo_access,
                        title = "Photos Access",
                        description = "To save image statuses from WhatsApp",
                        tint = Accent1
                    )

                    Divider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    PermissionItem(
                        iconResId = R.drawable.ic_video_access,
                        title = "Video Access",
                        description = "To save video statuses from WhatsApp",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Request button with improved styling
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Grant Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Privacy note with subtle styling
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "We value your privacy. These permissions are only used to access and save media statuses. No data is collected or shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    iconResId: Int,
    title: String,
    description: String,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tint.copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
} 