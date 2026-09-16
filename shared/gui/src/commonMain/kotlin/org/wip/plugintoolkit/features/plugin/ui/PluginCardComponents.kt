package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.viewmodel.AlternateRepoUpdate
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginActivityInfo
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownDivider
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_more_actions
import plugintoolkit.composeapp.generated.resources.plugin_action_refresh_locks
import plugintoolkit.composeapp.generated.resources.plugin_broken
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.plugin_loaded
import plugintoolkit.composeapp.generated.resources.plugin_newer_version_in_other_repo
import plugintoolkit.composeapp.generated.resources.plugin_open_folder
import plugintoolkit.composeapp.generated.resources.plugin_reload
import plugintoolkit.composeapp.generated.resources.plugin_rerun_setup
import plugintoolkit.composeapp.generated.resources.plugin_settings
import plugintoolkit.composeapp.generated.resources.plugin_settings_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_state_activating
import plugintoolkit.composeapp.generated.resources.plugin_state_active
import plugintoolkit.composeapp.generated.resources.plugin_state_deactivating
import plugintoolkit.composeapp.generated.resources.plugin_state_disabled
import plugintoolkit.composeapp.generated.resources.plugin_state_setting_up
import plugintoolkit.composeapp.generated.resources.plugin_state_setting_up_percent
import plugintoolkit.composeapp.generated.resources.plugin_state_updating
import plugintoolkit.composeapp.generated.resources.plugin_state_validating
import plugintoolkit.composeapp.generated.resources.plugin_status_validation_failed
import plugintoolkit.composeapp.generated.resources.plugin_step_setup
import plugintoolkit.composeapp.generated.resources.plugin_step_update
import plugintoolkit.composeapp.generated.resources.plugin_step_validation
import plugintoolkit.composeapp.generated.resources.plugin_switch_repo_action
import plugintoolkit.composeapp.generated.resources.plugin_uninstall
import plugintoolkit.composeapp.generated.resources.plugin_update
import plugintoolkit.composeapp.generated.resources.plugin_update_local
import plugintoolkit.composeapp.generated.resources.plugin_validate
import plugintoolkit.composeapp.generated.resources.plugin_validated
import plugintoolkit.composeapp.generated.resources.plugin_validation_pending
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_format

sealed class PluginStatusAction {
    object Uninstall : PluginStatusAction()
    object Reload : PluginStatusAction()
    object Validate : PluginStatusAction()
    object Update : PluginStatusAction()
    object RerunSetup : PluginStatusAction()
    object Changelog : PluginStatusAction()
    object Settings : PluginStatusAction()
    object RefreshLocks : PluginStatusAction()
    object OpenFolder : PluginStatusAction()
    data class Custom(val name: String) : PluginStatusAction()
}

internal data class CardButtonState(
    val reqAction: String?,
    val hasUpdate: Boolean,
    val hasAltUpdate: Boolean,
    val enabled: Boolean
)

/**
 * Information section of a plugin card containing its icon, name, status chips, version info,
 * error banners, alternate repository notifications, and active task progress.
 */
@Composable
internal fun PluginCardInfoSection(
    plugin: InstalledPlugin,
    isLoaded: Boolean,
    activity: PluginActivityInfo?,
    alternateUpdate: AlternateRepoUpdate?,
    onSwitchRepo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = activity?.progress ?: 0f
    val isDeterminate = activity?.isDeterminate == true && progress > 0f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(ToolkitTheme.dimensions.pluginIcon)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!plugin.isCompatible) {
                    ToolkitChip(
                        text = plugin.compatibilityError ?: "Incompatible",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else if (plugin.loadError != null) {
                    ToolkitChip(
                        text = stringResource(Res.string.plugin_broken),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else if (isLoaded) {
                    ToolkitChip(
                        text = stringResource(Res.string.plugin_loaded),
                        containerColor = ToolkitTheme.colors.success,
                        contentColor = ToolkitTheme.colors.onSuccess,
                    )
                }

                if (plugin.requiredAction != null) {
                    val badgeText = if (plugin.requiredAction == "CONFIGURE_SETTINGS") "Setup Required" else "Action Required"
                    ToolkitChip(
                        text = badgeText,
                        containerColor = ToolkitTheme.colors.warning,
                        contentColor = ToolkitTheme.colors.onWarning
                    )
                }

                if (activity != null) {
                    val chipText = when (activity.type) {
                        JobType.Setup -> if (isDeterminate) {
                            stringResource(Res.string.plugin_state_setting_up_percent, (progress * 100).toInt().coerceIn(0, 100))
                        } else {
                            stringResource(Res.string.plugin_state_setting_up)
                        }
                        JobType.Validation -> stringResource(Res.string.plugin_state_validating)
                        JobType.Update -> stringResource(Res.string.plugin_state_updating, (progress * 100).toInt().coerceIn(0, 100))
                        else -> stringResource(Res.string.plugin_state_activating)
                    }
                    ToolkitChip(
                        text = chipText,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (plugin.isCompatible && plugin.loadError == null) {
                    if (plugin.isValidated) {
                        ToolkitChip(
                            text = stringResource(Res.string.plugin_validated),
                            contentColor = ToolkitTheme.colors.validated,
                            containerColor = ToolkitTheme.colors.onValidated
                        )
                    } else if (plugin.isSetupCompleted) {
                        ToolkitChip(
                            text = stringResource(Res.string.plugin_status_validation_failed),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ToolkitChip(
                            text = stringResource(Res.string.plugin_validation_pending),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text(
                text = stringResource(Res.string.plugin_version_pkg_format, plugin.version, plugin.pkg),
                style = MaterialTheme.typography.bodySmall,
                color = if (plugin.loadError != null || !plugin.isCompatible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (plugin.supportedOs.isNotEmpty()) {
                Text(
                    text = "Supported OS: ${plugin.supportedOs.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val compatError = plugin.compatibilityError
            val loadError = plugin.loadError
            if (!plugin.isCompatible && compatError != null) {
                Text(
                    text = compatError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                )
            } else if (loadError != null) {
                Text(
                    text = loadError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                )
            }

            if (alternateUpdate != null) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                PluginCardAlternateUpdateBanner(
                    alternateUpdate = alternateUpdate,
                    onSwitchRepo = { onSwitchRepo(plugin.pkg) }
                )
            }

            if (activity != null) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                PluginCardProgress(activity = activity)
            }
        }
    }
}

/**
 * Banner notifying that a newer plugin version is available in an alternate repository.
 */
@Composable
internal fun PluginCardAlternateUpdateBanner(
    alternateUpdate: AlternateRepoUpdate,
    onSwitchRepo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = modifier
            .clickable(onClick = onSwitchRepo)
            .padding(top = ToolkitTheme.spacing.extraSmall / 2)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.small,
                vertical = ToolkitTheme.spacing.extraExtraSmall
            )
        ) {
            Icon(
                imageVector = Icons.Default.Upgrade,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(
                text = stringResource(
                    Res.string.plugin_newer_version_in_other_repo,
                    alternateUpdate.newerVersion,
                    alternateUpdate.repo.name
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(
                text = stringResource(Res.string.plugin_switch_repo_action),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Progress indicator for active setup, update, or validation background tasks.
 */
@Composable
internal fun PluginCardProgress(
    activity: PluginActivityInfo,
    modifier: Modifier = Modifier
) {
    val progress = activity.progress
    val isDeterminate = activity.isDeterminate && progress > 0f

    Column(modifier = modifier) {
        val stepText = when (activity.type) {
            JobType.Setup -> stringResource(Res.string.plugin_step_setup)
            JobType.Validation -> stringResource(Res.string.plugin_step_validation)
            JobType.Update -> stringResource(Res.string.plugin_step_update)
            else -> activity.step ?: stringResource(Res.string.plugin_state_activating)
        }
        Text(
            text = stepText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.extraSmall / 2)
        )
        if (isDeterminate) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ToolkitTheme.spacing.extraSmall),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ToolkitTheme.spacing.extraSmall),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Action buttons row for plugin management (execute required action, update, toggle state, settings, menu).
 */
@Composable
internal fun PluginCardActions(
    plugin: InstalledPlugin,
    customActions: List<PluginAction>,
    alternateUpdate: AlternateRepoUpdate?,
    hasUpdate: Boolean,
    enabled: Boolean,
    activity: PluginActivityInfo?,
    isCompact: Boolean,
    onToggle: (Boolean) -> Unit,
    onSwitchRepo: (String) -> Unit,
    onAction: (PluginStatusAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = activity != null && activity.isBusy
    val progress = activity?.progress ?: 0f
    val isDeterminate = activity?.isDeterminate == true && progress > 0f
    var menuExpanded by remember { mutableStateOf(false) }

    val buttonState = CardButtonState(
        reqAction = plugin.requiredAction,
        hasUpdate = hasUpdate,
        hasAltUpdate = alternateUpdate != null,
        enabled = enabled
    )

    AnimatedContent(
        targetState = buttonState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier
    ) { state ->
        val (reqAction, isUpdateAvailable, hasAltUpdate, readyStatus) = state
        ToolkitButtonGroup {
            if (reqAction != null) {
                val action = customActions.find { it.functionName == reqAction }
                val actionLabel = if (reqAction == "CONFIGURE_SETTINGS") "Configure" else (action?.name ?: "Fix Issue")
                item { shape, modifierSpec ->
                    Button(
                        onClick = {
                            if (reqAction == "CONFIGURE_SETTINGS") {
                                onAction(PluginStatusAction.Settings)
                            } else {
                                onAction(PluginStatusAction.Custom(reqAction))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ToolkitTheme.colors.warning),
                        shape = shape,
                        modifier = modifierSpec.tooltip(actionLabel),
                        enabled = readyStatus
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = actionLabel,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                text = actionLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { shape, modifierSpec ->
                if (isUpdateAvailable) {
                    val updateLabel = stringResource(Res.string.plugin_update)
                    Button(
                        onClick = { onAction(PluginStatusAction.Update) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = shape,
                        modifier = modifierSpec.tooltip(updateLabel),
                        enabled = readyStatus
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upgrade,
                            contentDescription = updateLabel,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                text = updateLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (hasAltUpdate) {
                    val switchLabel = stringResource(Res.string.plugin_switch_repo_action)
                    Button(
                        onClick = { onSwitchRepo(plugin.pkg) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = shape,
                        modifier = modifierSpec.tooltip(switchLabel),
                        enabled = readyStatus
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upgrade,
                            contentDescription = switchLabel,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                text = switchLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    val updateLocalLabel = stringResource(Res.string.plugin_update_local)
                    FilledTonalButton(
                        onClick = { onAction(PluginStatusAction.Update) },
                        shape = shape,
                        modifier = modifierSpec.tooltip(updateLocalLabel),
                        enabled = readyStatus
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = updateLocalLabel,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        if (!isCompact) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                text = updateLocalLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { shape, modifierSpec ->
                val toggleColor = if (plugin.isEnabled) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                }
                val toggleText = when {
                    activity != null -> when (activity.type) {
                        JobType.Setup -> if (isDeterminate) {
                            stringResource(Res.string.plugin_state_setting_up_percent, (progress * 100).toInt().coerceIn(0, 100))
                        } else {
                            stringResource(Res.string.plugin_state_setting_up)
                        }
                        JobType.Validation -> stringResource(Res.string.plugin_state_validating)
                        JobType.Update -> stringResource(Res.string.plugin_state_updating, (progress * 100).toInt().coerceIn(0, 100))
                        else -> if (plugin.isEnabled) {
                            stringResource(Res.string.plugin_state_activating)
                        } else {
                            stringResource(Res.string.plugin_state_deactivating)
                        }
                    }
                    else -> if (plugin.isEnabled) {
                        stringResource(Res.string.plugin_state_active)
                    } else {
                        stringResource(Res.string.plugin_state_disabled)
                    }
                }
                FilledTonalButton(
                    onClick = { onToggle(!plugin.isEnabled) },
                    colors = toggleColor,
                    shape = shape,
                    modifier = modifierSpec.tooltip(toggleText),
                    enabled = readyStatus && !isBusy
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            progress = { if (isDeterminate) progress else 0f },
                            modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                            strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                            color = if (plugin.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = if (plugin.isEnabled) Icons.Default.CheckCircle else Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.toggleButtonIconSize)
                        )
                    }
                    if (!isCompact) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                        Text(
                            text = toggleText,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { shape, modifierSpec ->
                FilledTonalIconButton(
                    onClick = { onAction(PluginStatusAction.Settings) },
                    shape = shape,
                    modifier = modifierSpec
                        .size(ToolkitTheme.dimensions.standardButtonHeight)
                        .tooltip(Res.string.plugin_settings_tooltip),
                    enabled = readyStatus && !isBusy
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(Res.string.plugin_settings)
                    )
                }
            }

            item { shape, modifierSpec ->
                Box {
                    FilledTonalIconButton(
                        onClick = { menuExpanded = true },
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.standardButtonHeight),
                        enabled = readyStatus && !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.action_more_actions)
                        )
                    }
                    PluginCardOverflowMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        plugin = plugin,
                        hasUpdate = hasUpdate,
                        alternateUpdate = alternateUpdate,
                        onSwitchRepo = onSwitchRepo,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

/**
 * Overflow dropdown menu with secondary plugin operations.
 */
@Composable
internal fun PluginCardOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    plugin: InstalledPlugin,
    hasUpdate: Boolean,
    alternateUpdate: AlternateRepoUpdate?,
    onSwitchRepo: (String) -> Unit,
    onAction: (PluginStatusAction) -> Unit,
    modifier: Modifier = Modifier
) {
    ToolkitDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        if (alternateUpdate != null) {
            ToolkitDropdownMenuItem(
                text = {
                    Text(
                        stringResource(Res.string.plugin_switch_repo_action) + " (${alternateUpdate.repo.name})"
                    )
                },
                onClick = {
                    onDismiss()
                    onSwitchRepo(plugin.pkg)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Upgrade,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            ToolkitDropdownMenuItem(
                text = { Text(stringResource(Res.string.plugin_update_local)) },
                onClick = {
                    onAction(PluginStatusAction.Update)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
            )
            ToolkitDropdownDivider()
        }

        if (hasUpdate && alternateUpdate == null) {
            ToolkitDropdownMenuItem(
                text = { Text(stringResource(Res.string.plugin_update_local)) },
                onClick = {
                    onAction(PluginStatusAction.Update)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
            )
            ToolkitDropdownDivider()
        }

        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_validate)) },
            onClick = {
                onAction(PluginStatusAction.Validate)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
        )
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_rerun_setup)) },
            onClick = {
                onAction(PluginStatusAction.RerunSetup)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
        )
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_reload)) },
            onClick = {
                onAction(PluginStatusAction.Reload)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) }
        )
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_changelog)) },
            onClick = {
                onAction(PluginStatusAction.Changelog)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
        )
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_action_refresh_locks)) },
            onClick = {
                onAction(PluginStatusAction.RefreshLocks)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
        )
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_open_folder)) },
            onClick = {
                onAction(PluginStatusAction.OpenFolder)
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
        )
        ToolkitDropdownDivider()
        ToolkitDropdownMenuItem(
            text = { Text(stringResource(Res.string.plugin_uninstall)) },
            onClick = {
                onAction(PluginStatusAction.Uninstall)
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null
                )
            },
            isDestructive = true
        )
    }
}
