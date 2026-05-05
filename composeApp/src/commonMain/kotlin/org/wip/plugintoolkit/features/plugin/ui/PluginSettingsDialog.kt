package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun PluginSettingsDialog(
    pkg: String,
    onDismiss: () -> Unit,
    viewModel: PluginSettingsViewModel = koinInject(parameters = { parametersOf(pkg) })
) {
    val store by viewModel.store.collectAsState()
    val manifest = viewModel.manifest ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.plugin_settings_title, manifest.plugin.name),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = ToolkitTheme.spacing.small)
            ) {
                // 1. Custom Settings (from manifest.settings)
                if (!manifest.settings.isNullOrEmpty()) {
                    SettingsGroup(title = "Custom Settings") {
                        manifest.settings!!.forEach { (key, meta) ->
                            val value = store.settings[key] ?: meta.defaultValue
                            DynamicParameterInput(
                                name = key,
                                metadata = ParameterMetadata(
                                    description = meta.description,
                                    type = meta.type,
                                    defaultValue = meta.defaultValue
                                ),
                                value = SettingsUtils.jsonToString(value, meta.type),
                                onValueChange = { viewModel.updateSetting(key, SettingsUtils.stringToJson(it, meta.type)) }
                            )
                        }
                    }
                }

                // 2. Global Parameter Defaults (from manifest.defaultParameters)
                if (!manifest.defaultParameters.isNullOrEmpty()) {
                    SettingsGroup(title = "Global Parameter Defaults") {
                        manifest.defaultParameters!!.forEach { (key, meta) ->
                            val value = store.globalParams[key] ?: meta.defaultValue
                            DynamicParameterInput(
                                name = key,
                                metadata = meta,
                                value = SettingsUtils.jsonToString(value, meta.type),
                                onValueChange = { viewModel.updateGlobalParam(key, SettingsUtils.stringToJson(it, meta.type)) }
                            )
                        }
                    }
                }

                // 3. Capability Parameter Defaults (grouped by capability)
                manifest.capabilities.filter { !it.parameters.isNullOrEmpty() }.forEach { capability ->
                    SettingsGroup(title = "Capability: ${capability.name}") {
                        capability.parameters!!.forEach { (key, meta) ->
                            val value = store.capabilityParams[capability.name]?.get(key) ?: meta.defaultValue
                            DynamicParameterInput(
                                name = key,
                                metadata = meta,
                                value = SettingsUtils.jsonToString(value, meta.type),
                                onValueChange = { viewModel.updateCapabilityParam(capability.name, key, SettingsUtils.stringToJson(it, meta.type)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.save(); onDismiss() }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
