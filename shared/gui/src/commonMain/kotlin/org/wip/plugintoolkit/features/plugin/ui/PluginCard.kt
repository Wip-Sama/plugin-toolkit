package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.viewmodel.AlternateRepoUpdate
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginActivityInfo
import org.wip.plugintoolkit.shared.components.ToolkitCard

/**
 * Primary card presentation for an installed plugin in the plugin manager.
 */
@Composable
fun PluginCard(
    plugin: InstalledPlugin,
    isLoaded: Boolean,
    hasUpdate: Boolean,
    customActions: List<PluginAction>,
    alternateUpdate: AlternateRepoUpdate? = null,
    enabled: Boolean = true,
    activity: PluginActivityInfo? = null,
    onToggle: (Boolean) -> Unit,
    onSwitchRepo: (String) -> Unit = {},
    onAction: (PluginStatusAction) -> Unit,
    onClick: () -> Unit
) {
    ToolkitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isLoaded) onClick else null
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < ToolkitTheme.dimensions.breakpointCompact

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PluginCardInfoSection(
                    plugin = plugin,
                    isLoaded = isLoaded,
                    activity = activity,
                    alternateUpdate = alternateUpdate,
                    onSwitchRepo = onSwitchRepo,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                PluginCardActions(
                    plugin = plugin,
                    customActions = customActions,
                    alternateUpdate = alternateUpdate,
                    hasUpdate = hasUpdate,
                    enabled = enabled,
                    activity = activity,
                    isCompact = isCompact,
                    onToggle = onToggle,
                    onSwitchRepo = onSwitchRepo,
                    onAction = onAction
                )
            }
        }
    }
}
