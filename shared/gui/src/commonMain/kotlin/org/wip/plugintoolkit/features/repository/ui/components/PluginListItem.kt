package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.utils.PluginCompatibilityUtils
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.PluginInstallationJobState
import org.wip.plugintoolkit.shared.components.ToolkitCard
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_install
import plugintoolkit.composeapp.generated.resources.plugin_available_in
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.repo_action_cancel_update
import plugintoolkit.composeapp.generated.resources.repo_action_queued
import plugintoolkit.composeapp.generated.resources.repo_action_update_version
import plugintoolkit.composeapp.generated.resources.repo_action_updating
import plugintoolkit.composeapp.generated.resources.repo_badge_local
import plugintoolkit.composeapp.generated.resources.repo_badge_remote
import plugintoolkit.composeapp.generated.resources.repo_downgrade_version
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_update
import plugintoolkit.composeapp.generated.resources.repo_incompatible_badge
import plugintoolkit.composeapp.generated.resources.repo_install_version
import plugintoolkit.composeapp.generated.resources.repo_plugin_installed_version_format
import plugintoolkit.composeapp.generated.resources.repo_plugin_pkg_version_format
import plugintoolkit.composeapp.generated.resources.repo_reinstall_version
import plugintoolkit.composeapp.generated.resources.repo_source_this

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PluginListItem(
    plugin: ExtensionPlugin,
    currentRepo: ExtensionRepo,
    installedPlugins: List<InstalledPlugin>,
    isRefreshing: Boolean,
    activeJobs: Map<String, PluginInstallationJobState>,
    onSetPackageSource: (String, String) -> Unit,
    conflicts: Map<String, List<ExtensionRepo>>,
    packageSourceOverrides: Map<String, String> = emptyMap(),
    pluginsMap: Map<String, List<ExtensionPlugin>> = emptyMap(),
    onInstall: (ExtensionPlugin) -> Unit,
    onCancel: (String) -> Unit,
    onShowChangelog: (ExtensionPlugin) -> Unit = {}
) {
    val pluginConflicts = conflicts[plugin.pkg]
    val selectedSourceRepo = if (!pluginConflicts.isNullOrEmpty()) {
        val overrideUrl = packageSourceOverrides[plugin.pkg]
        pluginConflicts.find { it.url == overrideUrl }
            ?: pluginConflicts.find { it.url == currentRepo.url }
            ?: pluginConflicts.first()
    } else {
        currentRepo
    }

    val installedPlugin = installedPlugins.find { it.pkg == plugin.pkg }
    val installedVersion = installedPlugin?.version
    val isInstalled = installedPlugin != null
    val installState = activeJobs[plugin.pkg]
    val hasUpdate = installedVersion != null && org.wip.plugintoolkit.core.utils.VersionUtils.compare(
        plugin.version,
        installedVersion
    ) > 0
    val (isCompatible, _) = PluginCompatibilityUtils.checkCompatibility(plugin)

    ToolkitCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackVertically = maxWidth < ToolkitTheme.dimensions.breakpointCompact

            val actionsRow: @Composable () -> Unit = {
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    val changelogLabel = stringResource(Res.string.plugin_changelog)
                    ToolkitTooltipBox(tooltip = changelogLabel) {
                        IconButton(
                            onClick = { onShowChangelog(plugin) }
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = changelogLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                            )
                        }
                    }

                    if (installState != null) {
                        var isHovered by remember(plugin.pkg) { mutableStateOf(false) }

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
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (installState.status == JobStatus.Queued) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    Text(
                                        stringResource(Res.string.repo_action_queued),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = { installState.progress },
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                        strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    Text(
                                        stringResource(Res.string.repo_action_updating),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else if (hasUpdate) {
                        Button(
                            onClick = { onInstall(plugin) },
                            enabled = !isRefreshing && isCompatible,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upgrade,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                text = stringResource(Res.string.repo_action_update_version, plugin.version),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (isInstalled) {
                        val isDowngrade =
                            org.wip.plugintoolkit.core.utils.VersionUtils.compare(plugin.version, installedVersion!!) < 0
                        val label = if (isDowngrade) {
                            stringResource(Res.string.repo_downgrade_version, plugin.version)
                        } else {
                            stringResource(Res.string.repo_reinstall_version, plugin.version)
                        }

                        Button(
                            onClick = { onInstall(plugin) },
                            enabled = !isRefreshing && isCompatible,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                text = label,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Button(
                            onClick = { onInstall(plugin) },
                            enabled = !isRefreshing && isCompatible,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                text = stringResource(Res.string.repo_install_version, plugin.version),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (stackVertically) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    PluginListItemInfo(
                        plugin = plugin,
                        currentRepo = currentRepo,
                        installedVersion = installedVersion,
                        isInstalled = isInstalled,
                        hasUpdate = hasUpdate,
                        isCompatible = isCompatible,
                        pluginConflicts = pluginConflicts,
                        selectedSourceRepo = selectedSourceRepo,
                        onSetPackageSource = onSetPackageSource,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        actionsRow()
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PluginListItemInfo(
                        plugin = plugin,
                        currentRepo = currentRepo,
                        installedVersion = installedVersion,
                        isInstalled = isInstalled,
                        hasUpdate = hasUpdate,
                        isCompatible = isCompatible,
                        pluginConflicts = pluginConflicts,
                        selectedSourceRepo = selectedSourceRepo,
                        onSetPackageSource = onSetPackageSource,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                    actionsRow()
                }
            }
        }
    }
}

@Composable
private fun PluginListItemInfo(
    plugin: ExtensionPlugin,
    currentRepo: ExtensionRepo,
    installedVersion: String?,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    isCompatible: Boolean,
    pluginConflicts: List<ExtensionRepo>?,
    selectedSourceRepo: ExtensionRepo,
    onSetPackageSource: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isCompatible) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.badgeVertical)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.badgeVertical
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                stringResource(Res.string.repo_incompatible_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1
                            )
                        }
                    }
                } else if (hasUpdate) {
                    Surface(
                        color = ToolkitTheme.colors.warning.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.badgeVertical)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.badgeVertical
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upgrade,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro),
                                tint = ToolkitTheme.colors.warning
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                stringResource(Res.string.repo_filter_chip_update) + " ($installedVersion -> ${plugin.version})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ToolkitTheme.colors.warning,
                                maxLines = 1
                            )
                        }
                    }
                } else if (isInstalled) {
                    Surface(
                        color = ToolkitTheme.colors.success.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.badgeVertical)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.badgeVertical
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro),
                                tint = ToolkitTheme.colors.success
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                stringResource(Res.string.repo_plugin_installed_version_format, installedVersion!!),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ToolkitTheme.colors.success,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.badgeVertical)
                    ) {
                        Text(
                            text = "v${plugin.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.badgeVertical
                            )
                        )
                    }
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

            if (pluginConflicts != null && pluginConflicts.size > 1) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.plugin_available_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box {
                        ExpressiveMenu(
                            options = pluginConflicts,
                            selectedOption = selectedSourceRepo,
                            onOptionSelected = { onSetPackageSource(plugin.pkg, it.url) },
                            labelProvider = { repo ->
                                val isCurrent = repo.url == currentRepo.url
                                val isDuplicateName = pluginConflicts.count { it.name == repo.name } > 1
                                val extraInfo = buildList {
                                    if (isCurrent) add(stringResource(Res.string.repo_source_this))
                                    else if (isDuplicateName) {
                                        add(if (repo.isLocal) stringResource(Res.string.repo_badge_local) else stringResource(Res.string.repo_badge_remote))
                                    }
                                }
                                if (extraInfo.isNotEmpty()) {
                                    "${repo.name} ${extraInfo.joinToString(" ")}"
                                } else {
                                    repo.name
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
