package com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager
import com.wip.kpm_cpm_wotoolkit.features.job.model.BackgroundJob
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobType
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleLoader
import com.wip.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.random.Random

import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import com.wip.plugin.api.PluginResponse

import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleManager

class PluginViewModel(
    private val jobManager: JobManager,
    private val notificationService: com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService,
    private val moduleManager: ModuleManager
) : ViewModel() {
    var jarPath by mutableStateOf("C:\\Users\\sgroo\\AndroidStudioProjects\\CMP_desktop_test\\operations\\build\\libs\\operations.jar")
    var selectedPlugin by mutableStateOf<PluginEntry?>(null)
    var selectedCapability by mutableStateOf<Capability?>(null)
    var loadedPlugins by mutableStateOf(ModuleLoader.getPlugins())
    
    var saveResults by mutableStateOf(true) // Default to true as requested "ability to save"
    val activeJobs = jobManager.jobs // Flow<List<BackgroundJob>>
    
    val parameterValues = mutableStateMapOf<String, String>()

    init {
        // Sync with ModuleManager
        moduleManager.loadedModules
            .onEach { 
                loadedPlugins = ModuleLoader.getPlugins()
                // If the selected plugin was unloaded, clear it
                if (selectedPlugin != null && !it.contains(selectedPlugin?.getManifest()?.module?.id)) {
                    selectPlugin(null)
                }
            }
            .launchIn(viewModelScope)
    }


    fun selectPlugin(plugin: PluginEntry?) {
        selectedPlugin = plugin
        selectedCapability = null
    }

    fun selectCapability(capability: Capability?) {
        selectedCapability = capability
        parameterValues.clear()
        capability?.parameters?.forEach { (name, _) ->
            parameterValues[name] = ""
        }
    }

    fun loadPlugin() {
        viewModelScope.launch {
            // Hardcoded for demo, in real app we'd scan or ask user
            val result = ModuleLoader.loadPlugin(
                jarPath, 
            )
            if (result.isSuccess) {
                val plugin = result.getOrThrow()
                plugin.initialize()
                loadedPlugins = ModuleLoader.getPlugins()
                selectPlugin(plugin)
            }
        }
    }

    fun unloadPlugin() {
        viewModelScope.launch {
            ModuleLoader.unloadPlugin(jarPath)
            loadedPlugins = ModuleLoader.getPlugins()
            selectPlugin(null)
        }
    }

    fun executeCapability() {
        val capability = selectedCapability ?: return
        val plugin = selectedPlugin ?: return
        
        viewModelScope.launch {
            val params = selectedCapability?.let { capability ->
                parameterValues.toMap().mapValues { (name, value) ->
                    val meta = capability.parameters?.get(name)
                    if (meta != null) parseValue(value, meta.type) else JsonPrimitive(value)
                }
            } ?: emptyMap()

            val jobId = Random.nextInt(1000, 9999).toString()
            val job = BackgroundJob(
                id = jobId,
                name = "${plugin.getManifest().module.name}: ${capability.name}",
                type = JobType.Capability,
                pluginId = plugin.getManifest().module.id,
                capabilityName = capability.name,
                parameters = params
            )
            jobManager.enqueueJob(job)
            
            notificationService.notify(
                title = "Job Enqueued",
                message = "Executing ${capability.name} in background"
            )
        }
    }

    fun clearCapabilityHistory() {
        val pluginId = selectedPlugin?.getManifest()?.module?.id
        val capName = selectedCapability?.name
        if (pluginId != null && capName != null) {
            jobManager.removeJobs { 
                it.pluginId == pluginId && 
                it.capabilityName == capName && 
                (it.status == JobStatus.Completed || it.status == JobStatus.Failed || it.status == JobStatus.Cancelled)
            }
        }
    }

    private fun buildRequest(capability: Capability, values: Map<String, String>): PluginRequest {
        val params = mutableMapOf<String, JsonElement>()
        capability.parameters?.forEach { (name, meta) ->
            val stringValue = values[name] ?: ""
            params[name] = parseValue(stringValue, meta.type)
        }
        return PluginRequest(method = capability.name, parameters = params)
    }

    private fun parseValue(value: String, type: DataType): JsonElement {
        if (value.isBlank()) return JsonNull
        return when (type) {
            is DataType.Primitive -> {
                when (type.primitiveType) {
                    PrimitiveType.DOUBLE -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
                    PrimitiveType.INT -> JsonPrimitive(value.toIntOrNull() ?: 0)
                    PrimitiveType.BOOLEAN -> JsonPrimitive(value.lowercase() == "true")
                    PrimitiveType.STRING -> JsonPrimitive(value)
                    else -> JsonPrimitive(value)
                }
            }
            is DataType.Array -> {
                val items = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                buildJsonArray {
                    items.forEach { add(parseValue(it, type.items)) }
                }
            }
            is DataType.Object -> {
                try {
                    Json.parseToJsonElement(value)
                } catch (e: Exception) {
                    JsonPrimitive(value)
                }
            }
        }
    }
}
