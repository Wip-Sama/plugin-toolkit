package org.wip.plugintoolkit.shared.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsSearchQuery
import org.wip.plugintoolkit.features.settings.utils.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun SettingsGroup(
    title: String,
    collapsible: Boolean = false,
    initialExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val searchQuery = LocalSettingsSearchQuery.current
    val allSettings = LocalSettingsRegistry.current

    var isExpanded by remember { mutableStateOf(initialExpanded) }

    var hasVisibleItems = true
    if (searchQuery.isNotBlank() && allSettings.isNotEmpty()) {
        val settingsInGroup = allSettings.filter { it.sectionTitle.resolve().equals(title, ignoreCase = true) }
        if (settingsInGroup.isNotEmpty()) {
            hasVisibleItems = settingsInGroup.any {
                it.title.resolve().contains(searchQuery, ignoreCase = true) ||
                        (it.subtitle?.resolve()?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        // Auto-expand if searching
        if (hasVisibleItems && searchQuery.isNotBlank()) {
            isExpanded = true
        }
    }

    AnimatedVisibility(
        visible = hasVisibleItems,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ToolkitTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ToolkitTheme.spacing.small, bottom = ToolkitTheme.spacing.extraSmall),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (collapsible) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(ToolkitTheme.spacing.medium),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(modifier = Modifier.padding(ToolkitTheme.spacing.extraSmall)) {
                        content()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsGroupPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SettingsGroup(title = "Appearance") {
                Text("Item 1", modifier = Modifier.padding(12.dp))
                Text("Item 2", modifier = Modifier.padding(12.dp))
            }
        }
    }
}

