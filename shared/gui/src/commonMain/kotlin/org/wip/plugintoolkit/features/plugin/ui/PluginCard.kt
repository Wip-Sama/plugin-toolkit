package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.viewmodel.AlternateRepoUpdate
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginActivityInfo
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitCard
import org.wip.plugintoolkit.shared.components.ToolkitChip
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

private data class CardButtonState(
    val reqAction: String?,
    val hasUpdate: Boolean,
    val hasAltUpdate: Boolean,
    val enabled: Boolean
)

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
    val statusColor = if (plugin.loadError != null) MaterialTheme.colorScheme.error
    else if (plugin.requiredAction != null) ToolkitTheme.colors.warning
    else if (isLoaded) ToolkitTheme.colors.success
    else if (plugin.isValidated) ToolkitTheme.colors.validated
    else MaterialTheme.colorScheme.outline

    val isBusy = activity != null && activity.isBusy
    val progress = activity?.progress ?: 0f
    val isDeterminate = activity?.isDeterminate == true && progress > 0f

    ToolkitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isLoaded) onClick else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Placeholder
            Box(
                modifier = Modifier
                    .size(ToolkitTheme.dimensions.pluginIcon)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!plugin.isCompatible) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        ToolkitChip(
                            text = plugin.compatibilityError ?: "Incompatible",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else if (plugin.loadError != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        ToolkitChip(
                            text = stringResource(Res.string.plugin_broken),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else if (isLoaded) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        ToolkitChip(
                            stringResource(Res.string.plugin_loaded),
                            containerColor = ToolkitTheme.colors.success,
                            contentColor = ToolkitTheme.colors.onSuccess, //TODO: do not like having the same color here
                        )
                    }

                    if (plugin.requiredAction != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        val badgeText =
                            if (plugin.requiredAction == "CONFIGURE_SETTINGS") "Setup Required" else "Action Required"
                        ToolkitChip(
                            badgeText,
                            containerColor = ToolkitTheme.colors.warning,
                            contentColor = ToolkitTheme.colors.onWarning //TODO: not liking having the same color here
                        )
                    }

                    if (activity != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
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
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            ToolkitChip(
                                text = stringResource(Res.string.plugin_validated),
                                contentColor = ToolkitTheme.colors.validated,
                                containerColor = ToolkitTheme.colors.onValidated
                            )
                        } else if (plugin.isSetupCompleted) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            ToolkitChip(
                                text = stringResource(Res.string.plugin_status_validation_failed),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            ToolkitChip(
                                text = stringResource(Res.string.plugin_validation_pending),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.plugin_version_pkg_format, plugin.version, plugin.pkg),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (plugin.loadError != null || !plugin.isCompatible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plugin.supportedOs.isNotEmpty()) {
                    Text(
                        "Supported OS: ${plugin.supportedOs.joinToString { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val compatError = plugin.compatibilityError
                val loadError = plugin.loadError
                if (!plugin.isCompatible && compatError != null) {
                    Text(
                        compatError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                    )
                } else if (loadError != null) {
                    Text(
                        loadError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                    )
                }

                if (alternateUpdate != null) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable { onSwitchRepo(plugin.pkg) }
                            .padding(top = ToolkitTheme.spacing.extraSmall / 2)
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                stringResource(
                                    Res.string.plugin_newer_version_in_other_repo,
                                    alternateUpdate.newerVersion,
                                    alternateUpdate.repo.name
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(
                                stringResource(Res.string.plugin_switch_repo_action),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (activity != null) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
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

            // Actions
            var expanded by remember { mutableStateOf(false) }

            val buttonState = CardButtonState(plugin.requiredAction, hasUpdate, alternateUpdate != null, enabled)
            AnimatedContent(
                targetState = buttonState,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { state ->
                val (reqAction, isUpdateAvailable, hasAltUpdate, readyStatus) = state
                ToolkitButtonGroup {
                    if (reqAction != null) {
                        val action = customActions.find { it.functionName == reqAction }
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
                                modifier = modifierSpec,
                                enabled = readyStatus
                            ) {
                                Text(
                                    if (reqAction == "CONFIGURE_SETTINGS") "Configure" else (action?.name
                                        ?: "Fix Issue")
                                )
                            }
                        }
                    }

                    item { shape, modifierSpec ->
                        if (isUpdateAvailable) {
                            Button(
                                onClick = { onAction(PluginStatusAction.Update) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = shape,
                                modifier = modifierSpec,
                                enabled = readyStatus
                            ) {
                                Text(stringResource(Res.string.plugin_update))
                            }
                        } else if (hasAltUpdate) {
                            Button(
                                onClick = { onSwitchRepo(plugin.pkg) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = shape,
                                modifier = modifierSpec,
                                enabled = readyStatus
                            ) {
                                Text(stringResource(Res.string.plugin_switch_repo_action))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onAction(PluginStatusAction.Update) },
                                shape = shape,
                                modifier = modifierSpec,
                                enabled = readyStatus
                            ) {
                                Text(stringResource(Res.string.plugin_update_local))
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
                            modifier = modifierSpec,
                            enabled = readyStatus && !isBusy
                        ) {
                            if (isBusy) {
                                if (isDeterminate) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                                        strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                                        color = if (plugin.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                                        strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                                        color = if (plugin.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (plugin.isEnabled) Icons.Default.CheckCircle else Icons.Default.Extension,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.toggleButtonIconSize)
                                )
                            }
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(text = toggleText)
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
                                Icons.Default.Settings,
                                contentDescription = stringResource(Res.string.plugin_settings)
                            )
                        }
                    }

                    item { shape, modifierSpec ->
                        Box {
                            FilledTonalIconButton(
                                onClick = { expanded = true },
                                shape = shape,
                                modifier = modifierSpec.size(ToolkitTheme.dimensions.standardButtonHeight),
                                enabled = readyStatus && !isBusy
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(Res.string.action_more_actions)
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                if (alternateUpdate != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    Res.string.plugin_switch_repo_action
                                                ) + " (${alternateUpdate.repo.name})"
                                            )
                                        },
                                        onClick = {
                                            expanded = false
                                            onSwitchRepo(plugin.pkg)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Upgrade,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.plugin_update_local)) },
                                        onClick = { onAction(PluginStatusAction.Update); expanded = false },
                                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_validate)) },
                                    onClick = { onAction(PluginStatusAction.Validate); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_rerun_setup)) },
                                    onClick = { onAction(PluginStatusAction.RerunSetup); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_reload)) },
                                    onClick = { onAction(PluginStatusAction.Reload); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_changelog)) },
                                    onClick = { onAction(PluginStatusAction.Changelog); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_action_refresh_locks)) },
                                    onClick = { onAction(PluginStatusAction.RefreshLocks); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.plugin_open_folder)) },
                                    onClick = { onAction(PluginStatusAction.OpenFolder); expanded = false },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(Res.string.plugin_uninstall),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { onAction(PluginStatusAction.Uninstall); expanded = false },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
