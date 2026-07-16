package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_install
import plugintoolkit.composeapp.generated.resources.repo_action_cancel_update
import plugintoolkit.composeapp.generated.resources.repo_action_update_version
import plugintoolkit.composeapp.generated.resources.repo_action_updating
import plugintoolkit.composeapp.generated.resources.repo_plugin_installed_version_format
import plugintoolkit.composeapp.generated.resources.repo_plugin_pkg_version_format
import plugintoolkit.composeapp.generated.resources.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PluginListItem(
    plugin: ExtensionPlugin,
    currentRepo: ExtensionRepo,
    installedPlugins: List<InstalledPlugin>,
    isRefreshing: Boolean,
    activeJobs: Map<String, Float>,
    onSetPackageSource: (String, String) -> Unit,
    conflicts: Map<String, List<ExtensionRepo>>,
    onInstall: (ExtensionPlugin) -> Unit,
    onCancel: (String) -> Unit,
) {
    val installedPlugin = installedPlugins.find { it.pkg == plugin.pkg }
    val installedVersion = installedPlugin?.version
    val isInstalled = installedPlugin != null
    val progress = activeJobs[plugin.pkg]
    val hasUpdate = installedVersion != null && org.wip.plugintoolkit.core.utils.VersionUtils.compare(
        plugin.version,
        installedVersion
    ) > 0

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plugin Icon
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(ToolkitTheme.dimensions.listIconSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ToolkitTheme.dimensions.listIconContentSize)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            // Plugin Information
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                    if (isInstalled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.badgeVertical)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.badgeVertical)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                Text(
                                    stringResource(Res.string.repo_plugin_installed_version_format, installedVersion!!),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(Res.string.repo_plugin_pkg_version_format, plugin.pkg, plugin.version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!plugin.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                    Text(
                        text = plugin.description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val pluginConflicts = conflicts[plugin.pkg]
                if (pluginConflicts != null && pluginConflicts.size > 1) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.plugin_available_in),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                        Box(modifier = Modifier.height(ToolkitTheme.dimensions.iconMedium)) {
                            ExpressiveMenu(
                                options = pluginConflicts,
                                selectedOption = pluginConflicts.firstOrNull { it.url == currentRepo.url }
                                    ?: pluginConflicts.first(),
                                onOptionSelected = { onSetPackageSource(plugin.pkg, it.url) },
                                labelProvider = { it.name }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            // Action Buttons
            if (progress != null) {
                var isHovered by remember { mutableStateOf(false) }

                Surface(
                    shape = CircleShape,
                    color = if (isHovered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isHovered) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                        .clickable { onCancel(plugin.pkg) }
                        .padding(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.badgeHorizontal)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                    ) {
                        if (isHovered) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                stringResource(Res.string.repo_action_cancel_update),
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                stringResource(Res.string.repo_action_updating),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else if (isInstalled) {
                val isDowngrade =
                    org.wip.plugintoolkit.core.utils.VersionUtils.compare(plugin.version, installedVersion!!) < 0
                val buttonColor =
                    if (hasUpdate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                val contentColor =
                    if (hasUpdate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

                Button(
                    onClick = { onInstall(plugin) },
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = contentColor
                    )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    val label = when {
                        hasUpdate -> stringResource(Res.string.repo_action_update_version, plugin.version)
                        isDowngrade -> "Downgrade to ${plugin.version}"
                        else -> "Reinstall ${plugin.version}"
                    }
                    Text(label)
                }
            } else {
                Button(
                    onClick = { onInstall(plugin) },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    Text(stringResource(Res.string.action_install))
                }
            }
        }
    }
}
