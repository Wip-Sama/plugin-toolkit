package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.LocalSettingsSearchQuery
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.LocalSettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.resolve

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val searchQuery = LocalSettingsSearchQuery.current
    val allSettings = LocalSettingsRegistry.current

    var hasVisibleItems = true
    if (searchQuery.isNotBlank() && allSettings.isNotEmpty()) {
        val settingsInGroup = allSettings.filter { it.sectionTitle.resolve().equals(title, ignoreCase = true) }
        if (settingsInGroup.isNotEmpty()) {
            hasVisibleItems = settingsInGroup.any {
                it.title.resolve().contains(searchQuery, ignoreCase = true) ||
                (it.subtitle?.resolve()?.contains(searchQuery, ignoreCase = true) == true)
            }
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
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    content()
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

