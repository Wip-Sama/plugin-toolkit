package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.model.JobProgress
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.viewmodel.SinglePluginHostViewModel
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.plugin.JobResultCard
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear
import plugintoolkit.composeapp.generated.resources.plugin_capabilities
import plugintoolkit.composeapp.generated.resources.plugin_execution_results
import plugintoolkit.composeapp.generated.resources.plugin_rerun_setup
import plugintoolkit.composeapp.generated.resources.plugin_select_capability_hint
import plugintoolkit.composeapp.generated.resources.plugin_settings
import plugintoolkit.composeapp.generated.resources.search_capabilities_placeholder
import plugintoolkit.composeapp.generated.resources.standalone_no_capabilities
import plugintoolkit.composeapp.generated.resources.standalone_run_setup
import plugintoolkit.composeapp.generated.resources.standalone_setup_completed
import plugintoolkit.composeapp.generated.resources.standalone_setup_in_progress
import plugintoolkit.composeapp.generated.resources.standalone_setup_required_desc
import plugintoolkit.composeapp.generated.resources.standalone_setup_required_title

/**
 * Standalone application runner view.
 * Displays a dedicated single-plugin execution environment with Header, Setup step,
 * capability selection, execution parameters, and live job results.
 */
@Composable
fun SinglePluginRunnerView(
    viewModel: SinglePluginHostViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val manifest = viewModel.manifest
    val pluginId = viewModel.pluginId
    val selectedCapability = viewModel.selectedCapability

    val activeJobs by viewModel.activeJobs.collectAsState(initial = emptyList())
    val endedJobs by viewModel.endedJobs.collectAsState(initial = emptyList())
    val allJobs = remember(activeJobs, endedJobs) { activeJobs + endedJobs }

    val jobProgressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val jobLogsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())

    val capabilityJobs = remember(allJobs, selectedCapability) {
        val capName = selectedCapability?.name
        if (capName == null) emptyList()
        else allJobs.filter { it.pluginId == pluginId && it.capabilityName == capName }
            .sortedByDescending { it.enqueuedAt }
    }

    val pluginLocksState by viewModel.pluginLocksState.collectAsState()
    val pluginSettingsState by viewModel.pluginSettingsState.collectAsState()

    val providedLocks = remember(pluginId, pluginLocksState) {
        pluginLocksState[pluginId] ?: emptyMap()
    }

    val providedSettings = remember(pluginId, pluginSettingsState) {
        val store = pluginSettingsState[pluginId]
        val manifestDefaults = (manifest.settings?.mapValues { (_, meta) ->
            meta.defaultValue ?: if (meta.type is DataType.Primitive && (meta.type as DataType.Primitive).primitiveType == PrimitiveType.BOOLEAN) {
                JsonPrimitive(false)
            } else null
        }?.filterValues { it != null } ?: emptyMap()) as Map<String, kotlinx.serialization.json.JsonElement>
        manifestDefaults + (store?.settings ?: emptyMap()) + (store?.globalParams ?: emptyMap())
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var scrollToSettingKey by remember { mutableStateOf<String?>(null) }
    var capabilitySearchQuery by remember { mutableStateOf("") }

    if (showSettingsDialog) {
        PluginSettingsDialog(
            pkg = pluginId,
            scrollToSetting = scrollToSettingKey,
            onDismiss = {
                showSettingsDialog = false
                scrollToSettingKey = null
            }
        )
    }

    val isSetupRunning by viewModel.isSetupRunning.collectAsState()
    val isSetupCompleted by viewModel.isSetupCompleted.collectAsState()
    val isPluginReady by viewModel.isPluginReady.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top Bar: Plugin Header & Actions ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ToolkitTheme.spacing.large,
                        vertical = ToolkitTheme.spacing.medium
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PluginHeader(manifest = manifest)

                ToolkitButtonGroup {
                    if (viewModel.hasSetupHandler) {
                        item { shape, modifierSpec ->
                            if (isSetupRunning) {
                                FilledTonalButton(
                                    onClick = {},
                                    enabled = false,
                                    shape = shape,
                                    modifier = modifierSpec
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                        strokeWidth = ToolkitTheme.dimensions.circularProgressStrokeWidth
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(stringResource(Res.string.standalone_setup_in_progress))
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { viewModel.runSetup() },
                                    shape = shape,
                                    modifier = modifierSpec
                                ) {
                                    Icon(
                                        imageVector = if (isSetupCompleted) Icons.Default.CheckCircle else Icons.Default.Build,
                                        contentDescription = null,
                                        tint = if (isSetupCompleted) MaterialTheme.colorScheme.primary else ToolkitTheme.colors.warning,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(
                                        text = if (isSetupCompleted) {
                                            stringResource(Res.string.plugin_rerun_setup)
                                        } else {
                                            stringResource(Res.string.standalone_run_setup)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item { shape, modifierSpec ->
                        FilledTonalButton(
                            onClick = { showSettingsDialog = true },
                            shape = shape,
                            modifier = modifierSpec.tooltip(Res.string.plugin_settings)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(Res.string.plugin_settings),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.plugin_settings))
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider),
            thickness = ToolkitTheme.dimensions.borderThin
        )

        // --- Two-Pane Workspace ---
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Rail: Capability Selector
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .width(ToolkitTheme.dimensions.sidebarExpandedWidth)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ToolkitTheme.spacing.medium)
                ) {
                    Text(
                        text = stringResource(Res.string.plugin_capabilities),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
                    )

                    if (manifest.capabilities.size > 3 || capabilitySearchQuery.isNotEmpty()) {
                        ToolkitTextField(
                            value = capabilitySearchQuery,
                            onValueChange = { capabilitySearchQuery = it },
                            placeholder = {
                                Text(
                                    stringResource(Res.string.search_capabilities_placeholder),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            },
                            trailingIcon = if (capabilitySearchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { capabilitySearchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = ToolkitTheme.spacing.small),
                            singleLine = true
                        )
                    }

                    val filteredCapabilities = remember(manifest.capabilities, capabilitySearchQuery) {
                        if (capabilitySearchQuery.isBlank()) manifest.capabilities
                        else manifest.capabilities.filter { cap ->
                            cap.name.contains(capabilitySearchQuery, ignoreCase = true) ||
                                (cap.description?.contains(capabilitySearchQuery, ignoreCase = true) == true)
                        }
                    }

                    if (filteredCapabilities.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.standalone_no_capabilities),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            filteredCapabilities.forEach { capability ->
                                key(capability.name) {
                                    CapabilityItem(
                                        capability = capability,
                                        isSelected = selectedCapability?.name == capability.name,
                                        onClick = { viewModel.selectCapability(capability) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(ToolkitTheme.dimensions.borderThin),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )

            // Right Pane: Capability Tester & Execution Results
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (selectedCapability == null) {
                    EmptyState(stringResource(Res.string.plugin_select_capability_hint))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(ToolkitTheme.spacing.extraLarge)
                    ) {
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
                            pluginId = pluginId,
                            isPluginReady = isPluginReady,
                            notReadyReason = if (!isPluginReady) {
                                if (viewModel.hasSetupHandler && !isSetupCompleted) {
                                    stringResource(Res.string.standalone_setup_required_desc)
                                } else {
                                    stringResource(Res.string.standalone_setup_in_progress)
                                }
                            } else null,
                            onNavigateToPluginSetting = { _, settingKey ->
                                scrollToSettingKey = settingKey
                                showSettingsDialog = true
                            },
                            onExecute = { viewModel.executeCapability() },
                            onSetAsDefault = { viewModel.saveCurrentParametersAsDefault() },
                            onResetDefaults = { viewModel.resetParametersToDefault() }
                        )

                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraLarge))

                        if (capabilityJobs.isNotEmpty()) {
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
                                capabilityJobs.forEach { job ->
                                    key(job.id) {
                                        JobResultCard(
                                            job = job,
                                            progress = jobProgressMap[job.id] ?: JobProgress(),
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
    }
}
