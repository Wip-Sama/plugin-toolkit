package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.shared.components.plugin.JobResultCard
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear
import plugintoolkit.composeapp.generated.resources.action_reset_to_default
import plugintoolkit.composeapp.generated.resources.action_set_as_default
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability_running
import plugintoolkit.composeapp.generated.resources.plugin_execution_results
import plugintoolkit.composeapp.generated.resources.plugin_id_format
import plugintoolkit.composeapp.generated.resources.plugin_mem_format
import plugintoolkit.composeapp.generated.resources.plugin_no_parameters
import plugintoolkit.composeapp.generated.resources.plugin_select_capability_hint
import plugintoolkit.composeapp.generated.resources.plugin_tester_title
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun PluginContent(
    viewModel: PluginViewModel,
    scrollToSetting: String? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight()) {
        val selectedCapability = viewModel.selectedCapability
        val activeJobs by viewModel.activeJobs.collectAsState(initial = emptyList())
        val endedJobs by viewModel.endedJobs.collectAsState(initial = emptyList())
        val allJobs = remember(activeJobs, endedJobs) { activeJobs + endedJobs }

        val jobProgressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
        val jobLogsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
        val capabilityJobs = remember(allJobs, viewModel.selectedPlugin, selectedCapability) {
            val pluginId = viewModel.selectedPlugin?.getManifest()?.getOrThrow()?.plugin?.id
            val capName = selectedCapability?.name
            if (pluginId == null || capName == null) emptyList()
            else allJobs.filter { it.pluginId == pluginId && it.capabilityName == capName }
                .sortedByDescending { it.enqueuedAt }
        }

        val pluginManager: org.wip.plugintoolkit.features.plugin.logic.PluginManager = org.koin.compose.koinInject()
        val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
        val pluginSettingsState by pluginManager.pluginSettingsState.collectAsState()
        val pluginId = viewModel.selectedPlugin?.getManifest()?.getOrNull()?.plugin?.id

        var settingsDialogPkg by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(pluginId, scrollToSetting) {
            if (pluginId != null && scrollToSetting != null) {
                settingsDialogPkg = pluginId
            }
        }
        val currentSettingsPkg = settingsDialogPkg
        if (currentSettingsPkg != null) {
            PluginSettingsDialog(
                pkg = currentSettingsPkg,
                scrollToSetting = scrollToSetting,
                onDismiss = { settingsDialogPkg = null }
            )
        }

        LaunchedEffect(pluginId) {
            if (pluginId != null) {
                pluginManager.refreshLocks(pluginId)
            }
        }

        val providedLocks = remember(pluginId, pluginLocksState) {
            if (pluginId != null) {
                pluginLocksState[pluginId] ?: pluginLocksState.values.fold(emptyMap<String, Boolean>()) { acc, map -> acc + map }
            } else {
                emptyMap()
            }
        }
        val providedSettings = remember(pluginId, pluginSettingsState) {
            val store = if (pluginId != null) pluginSettingsState[pluginId] ?: pluginManager.loadPluginSettings(pluginId) else null
            val manifest = viewModel.selectedPlugin?.getManifest()?.getOrNull()
            val manifestDefaults = (manifest?.settings?.mapValues { (_, meta) ->
                meta.defaultValue ?: if (meta.type is DataType.Primitive && (meta.type as DataType.Primitive).primitiveType == PrimitiveType.BOOLEAN) {
                    JsonPrimitive(false)
                } else null
            }?.filterValues { it != null } ?: emptyMap()) as Map<String, kotlinx.serialization.json.JsonElement>
            manifestDefaults + (store?.settings ?: emptyMap()) + (store?.globalParams ?: emptyMap())
        }

        if (selectedCapability == null) {
            EmptyState(stringResource(Res.string.plugin_select_capability_hint))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ToolkitTheme.spacing.extraLarge)
            ) {
                // Tester Area
                CapabilityTester(
                    capability = selectedCapability,
                    parameterValues = viewModel.parameterValues.toMap(),
                    onParameterChange = { name, value -> viewModel.updateParameter(name, value) },
                    isParameterAutoGenerated = { name -> viewModel.isParameterAutoGenerated(name) },
                    saveResults = viewModel.saveResults,
                    onSaveResultsChange = { viewModel.saveResults = it },
                    activeJobs = capabilityJobs.filter { it.status == JobStatus.Running || it.status == JobStatus.Queued },
                    providedLocks = providedLocks,
                    providedSettings = providedSettings,
                    pluginId = pluginId ?: "",
                    onNavigateToPluginSetting = onNavigateToPluginSetting,
                    onExecute = { viewModel.executeCapability() },
                    onSetAsDefault = { viewModel.saveCurrentParametersAsDefault() },
                    onResetDefaults = { viewModel.resetParametersToDefault() }
                )

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraLarge))

                // History / Results Area
                val visibleJobs = capabilityJobs

                if (visibleJobs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = ToolkitTheme.spacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.plugin_execution_results),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(onClick = { viewModel.clearCapabilityHistory() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_clear))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                        visibleJobs.forEach { job ->
                            key(job.id) {
                                JobResultCard(
                                    job = job,
                                    progress = jobProgressMap[job.id] ?: org.wip.plugintoolkit.features.job.model.JobProgress(),
                                    logs = jobLogsMap[job.id] ?: emptyList(),
                                    onDelete = {
                                        if (job.status == JobStatus.Completed || job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                                            viewModel.removeEndedJob(job.id)
                                        } else {
                                            viewModel.cancelJob(job.id, force = true)
                                        }
                                    },
                                    onCancel = { force -> viewModel.cancelJob(job.id, force) },
                                    onPause = { viewModel.pauseJob(job.id) },
                                    onResume = { viewModel.resumeJob(job.id) },
                                    onClear = { viewModel.removeEndedJob(job.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginHeader(manifest: PluginManifest) {
    Column {
        Text(
            manifest.plugin.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            manifest.plugin.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.mediumSmall))
        Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
            Badge { Text(stringResource(Res.string.plugin_id_format, manifest.plugin.id)) }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(Res.string.plugin_mem_format, manifest.requirements.minMemoryMb))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CapabilityItem(
    capability: Capability,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = ToolkitTheme.shapes.medium,
        border = if (isSelected) BorderStroke(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium).fillMaxWidth()) {
            Text(capability.name, fontWeight = FontWeight.Bold)
            Text(
                capability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (capability.semanticTypes.isNotEmpty() || capability.fileAccess?.isDestructive == true) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                ) {
                    if (capability.fileAccess?.isDestructive == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
                            border = BorderStroke(ToolkitTheme.dimensions.borderThin, MaterialTheme.colorScheme.error.copy(alpha = ToolkitTheme.opacity.divider)),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text(
                                text = "Destructive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                            )
                        }
                    }
                    capability.semanticTypes.forEach { type ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.high),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text(
                                text = "${type.namespace}/${type.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityTester(
    capability: Capability,
    parameterValues: Map<String, String>,
    onParameterChange: (String, String) -> Unit,
    isParameterAutoGenerated: (String) -> Boolean,
    saveResults: Boolean,
    onSaveResultsChange: (Boolean) -> Unit,
    activeJobs: List<BackgroundJob>,
    providedLocks: Map<String, Boolean> = emptyMap(),
    providedSettings: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    pluginId: String = "",
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    onExecute: () -> Unit,
    onSetAsDefault: () -> Unit = {},
    onResetDefaults: () -> Unit = {}
) {
    val capabilityParameters = capability.parameters ?: emptyMap()
    val allParams = capabilityParameters.toMutableMap()

    if (saveResults && capability.returnType != org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.UNIT)) {
        allParams["_outputFile"] = org.wip.plugintoolkit.api.ParameterMetadata(
            description = "Optional file to save the capability's return result",
            type = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
            required = false,
            role = ParameterRole.OUTPUT_LOCATION,
            semanticTypes = emptyList()
        )
    }

    val executionParameters = allParams.map { (name, meta) ->
        org.wip.plugintoolkit.shared.components.plugin.ExecutionParameter(
            name = name,
            value = parameterValues[name] ?: "",
            metadata = meta,
            onValueChange = { newValue -> onParameterChange(name, newValue) },
            enabled = true,
            isAutoGenerated = isParameterAutoGenerated(name)
        )
    }

    org.wip.plugintoolkit.shared.components.plugin.ExecutionParametersCard(
        title = stringResource(Res.string.plugin_tester_title, capability.name),
        icon = Icons.Default.PlayArrow,
        description = null,
        fileAccess = capability.fileAccess,
        isDestructive = capability.fileAccess?.isDestructive == true || allParams.values.any { it.isDestructive },
        saveResults = saveResults,
        onSaveResultsChange = onSaveResultsChange,
        parameters = executionParameters,
        providedSettings = providedSettings,
        providedLocks = providedLocks,
        pluginId = pluginId,
        onNavigateToPluginSetting = onNavigateToPluginSetting
    )

    if (capability.parameters.isNullOrEmpty()) {
        Text(stringResource(Res.string.plugin_no_parameters), style = MaterialTheme.typography.bodyMedium)
    }

    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

    val validationErrors = remember(parameterValues.toMap(), capability.parameters) {
        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateAllParameters(
            parameterValues = parameterValues,
            parameters = capability.parameters
        )
    }
    val requirementError = remember(capability, providedLocks, providedSettings, parameterValues.toMap()) {
        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateCapabilityLocksAndSettings(
            capability = capability,
            providedLocks = providedLocks,
            providedSettings = providedSettings,
            parameterValues = parameterValues.toMap()
        )
    }
    val isValid = validationErrors.isEmpty() && requirementError == null

    val targetSetting = remember(capability, providedLocks, providedSettings, parameterValues.toMap()) {
        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.getCapabilityTargetSettingKey(
            capability = capability,
            providedLocks = providedLocks,
            providedSettings = providedSettings,
            parameterValues = parameterValues.toMap()
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onExecute,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onNavigateToPluginSetting != null) {
                        Modifier.lockedClickInterceptor(
                            isLocked = requirementError != null,
                            pluginId = pluginId,
                            targetSettingKey = targetSetting,
                            onNavigateToPluginSetting = onNavigateToPluginSetting
                        )
                    } else {
                        Modifier.lockedClickInterceptor(
                            isLocked = requirementError != null,
                            targetScreen = Screen.PluginManager(pluginId = pluginId.ifEmpty { null }, scrollToSetting = targetSetting.ifEmpty { null })
                        )
                    }
                ),
            shape = MaterialTheme.shapes.medium,
            enabled = isValid
        ) {
            if (activeJobs.isNotEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize),
                    strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                Text(stringResource(Res.string.plugin_execute_capability_running, activeJobs.size))
            } else {
                Text(stringResource(Res.string.plugin_execute_capability))
            }
        }

        OutlinedButton(
            onClick = onSetAsDefault,
            enabled = isValid,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(stringResource(Res.string.action_set_as_default))
        }

        OutlinedButton(
            onClick = onResetDefaults,
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(stringResource(Res.string.action_reset_to_default))
        }
    }
    if (requirementError != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = ToolkitTheme.spacing.extraSmall)
                .then(
                    if (onNavigateToPluginSetting != null) {
                        Modifier.lockedClickInterceptor(
                            isLocked = true,
                            pluginId = pluginId,
                            targetSettingKey = targetSetting,
                            onNavigateToPluginSetting = onNavigateToPluginSetting
                        )
                    } else {
                        Modifier.lockedClickInterceptor(
                            isLocked = true,
                            targetScreen = Screen.PluginManager(pluginId = pluginId.ifEmpty { null }, scrollToSetting = targetSetting.ifEmpty { null })
                        )
                    }
                )
        ) {
            LockedCapabilityIcon(modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize))
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Text(
                text = requirementError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    } else if (validationErrors.isNotEmpty()) {
        Text(
            text = "Fix parameter errors before executing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
        )
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraLarge),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = ToolkitTheme.opacity.divider)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
