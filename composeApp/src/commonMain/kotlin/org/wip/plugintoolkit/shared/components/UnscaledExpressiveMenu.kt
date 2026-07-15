package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.staticCompositionLocalOf
import org.wip.plugintoolkit.core.theme.ToolkitTheme

interface OverlayHost {
    fun show(bounds: Rect, onDismiss: () -> Unit, content: @Composable () -> Unit)
    fun hide()
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

@Composable
fun UnscaledExpressiveMenu(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    labelProvider: (String) -> String,
    enabled: Boolean = true,
    disabledOptions: Set<String> = emptySet()
) {
    var isExpanded by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val overlay = LocalOverlayHost.current
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                bounds = coordinates.boundsInRoot()
            }
            .clickable(enabled = enabled) {
                isExpanded = true
            }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = labelProvider(selectedOption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
    }
    
    LaunchedEffect(isExpanded, bounds) {
        if (isExpanded && bounds != null && overlay != null) {
            overlay.show(
                bounds = bounds!!,
                onDismiss = { isExpanded = false }
            ) {
                val menuWidthDp = with(density) { bounds!!.width.toDp() }
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(menuWidthDp)
                ) {
                    Column(modifier = Modifier.padding(vertical = ToolkitTheme.spacing.extraSmall)) {
                        options.forEach { option ->
                            val isDisabled = disabledOptions.contains(option)
                            DropdownMenuItem(
                                text = { Text(labelProvider(option)) },
                                onClick = {
                                    if (!isDisabled) {
                                        onOptionSelected(option)
                                        isExpanded = false
                                    }
                                },
                                enabled = !isDisabled
                            )
                        }
                    }
                }
            }
        } else if (!isExpanded && overlay != null) {
            overlay.hide()
        }
    }
}
