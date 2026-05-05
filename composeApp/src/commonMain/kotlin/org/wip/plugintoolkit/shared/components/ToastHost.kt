package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.settings.model.NotificationSettings
import kotlinx.coroutines.delay
import java.util.UUID

data class ToastData(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isNotification: Boolean,
    val durationMillis: Long
)

@Composable
fun ToastHost(
    notificationService: NotificationService,
    settings: NotificationSettings,
    modifier: Modifier = Modifier
) {
    val toastQueue = remember { mutableStateListOf<ToastData>() }
    var currentToast by remember { mutableStateOf<ToastData?>(null) }

    val currentSettings by rememberUpdatedState(settings)

    LaunchedEffect(notificationService) {
        notificationService.events.collect { event ->
            if (event is NotificationEvent.Toast) {
                val duration = if (!event.isNotification || !currentSettings.toastAutoDismiss) {
                    Long.MAX_VALUE
                } else {
                    currentSettings.toastDismissTime * 1000L
                }

                val newToast = ToastData(
                    message = event.message,
                    isNotification = event.isNotification,
                    durationMillis = duration
                )

                if (currentToast == null) {
                    currentToast = newToast
                } else {
                    toastQueue.add(newToast)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Invisible dummy to balance the layout and keep the toast perfectly centered
            AnimatedVisibility(
                visible = toastQueue.isNotEmpty() && currentToast != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.alpha(0f)) {
                        QueueControl(count = toastQueue.size, onClearAll = {})
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }

            AnimatedContent(
                targetState = currentToast,
                transitionSpec = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                            slideOutVertically(targetOffsetY = { it }) + fadeOut()
                },
                label = "ToastTransition"
            ) { toast ->
                if (toast != null) {
                    ToastItem(
                        toast = toast,
                        onDismiss = {
                            currentToast = if (toastQueue.isNotEmpty()) toastQueue.removeAt(0) else null
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = toastQueue.isNotEmpty() && currentToast != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    QueueControl(
                        count = toastQueue.size,
                        onClearAll = {
                            toastQueue.clear()
                            currentToast = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastData,
    onDismiss: () -> Unit
) {
    var progress by remember(toast.id) { mutableStateOf(1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (toast.durationMillis == Long.MAX_VALUE) 0 else 100, easing = LinearEasing),
        label = "ProgressAnimation"
    )

    LaunchedEffect(toast.id) {
        if (toast.durationMillis != Long.MAX_VALUE) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < toast.durationMillis) {
                progress = 1f - (System.currentTimeMillis() - startTime).toFloat() / toast.durationMillis
                delay(16)
            }
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 300.dp, max = 500.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = toast.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                if (toast.isNotification) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (toast.durationMillis != Long.MAX_VALUE) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
private fun QueueControl(
    count: Int,
    onClearAll: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val alpha by animateFloatAsState(if (isHovered) 1f else 0.4f)

    Surface(
        onClick = onClearAll,
        modifier = Modifier
            .hoverable(interactionSource)
            .alpha(alpha),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(count.toString())
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "Clear All",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
