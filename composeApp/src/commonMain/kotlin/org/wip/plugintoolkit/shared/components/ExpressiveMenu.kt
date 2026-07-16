package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.core.theme.ToolkitTheme

/**
 * A custom dropdown menu styled similarly to the Material 3 Expressive vertical menu.
 * 
 * Note: If you want to use the native new context menus in Compose Multiplatform,
 * you can enable it globally via:
 * `@OptIn(ExperimentalFoundationApi::class)`
 * `ComposeFoundationFlags.isNewContextMenuEnabled = true`
 */
@Composable
fun <T> ExpressiveMenu(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(ToolkitTheme.spacing.small)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else ToolkitTheme.colors.transparent
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = 10.dp)
                ) {
                    Text(
                        text = labelProvider(option),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Add a small spacer between items if they are not the last one
                if (option != options.last()) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExpressiveMenuPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            ExpressiveMenu(
                options = listOf("Option 1", "Option 2", "Option 3"),
                selectedOption = "Option 2",
                onOptionSelected = {},
                labelProvider = { it }
            )
        }
    }
}
