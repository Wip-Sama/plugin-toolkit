package com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager
import com.wip.kpm_cpm_wotoolkit.features.job.model.BackgroundJob
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobType
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.PluginLoader
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.PluginManager
import com.wip.plugin.api.Capability
import com.wip.plugin.api.DataType
import com.wip.plugin.api.PluginEntry
import com.wip.plugin.api.PluginRequest
import com.wip.plugin.api.PrimitiveType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

class PluginViewModel(
    private val jobManager: JobManager,
    private val notificationService: com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService,
    private val pluginManager: PluginManager
) : ViewModel() {
    var jarPath by mutableStateOf("")
    var selectedPlugin by mutableStateOf<PluginEntry?>(null)
    var selectedCapability by mutableStateOf<Capability?>(null)
    var loadedPlugins by mutableStateOf(PluginLoader.getPlugins())

    var saveResults by mutableStateOf(true) // Default to true as requested "ability to save"
    val activeJobs = jobManager.jobs // Flow<List<BackgroundJob>>
    val jobProgress = jobManager.jobProgress // Flow<Map<String, Float>>

    val parameterValues = mutableStateMapOf<String, String>()
    private val jobCounterMutex = Mutex()
    private var jobCounter = 0

    fun updateParameter(name: String, value: String) {
        parameterValues[name] = value
    }

    init {
        // Sync with PluginManager
        pluginManager.loadedPlugins
            .onEach {
                loadedPlugins = PluginLoader.getPlugins()
                // If the selected plugin was unloaded, clear it
                if (selectedPlugin != null && !it.contains(selectedPlugin?.getManifest()?.plugin?.id)) {
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
        capability?.parameters?.forEach { (name, meta) ->
            parameterValues[name] = meta.defaultValue?.let {
                if (it is JsonPrimitive && it.isString) it.content else it.toString()
            } ?: ""
        }
    }

    fun loadPlugin() {
        if (jarPath.isBlank()) {
            notificationService.notify(title = "Error", message = "Plugin path is empty")
            return
        }
        viewModelScope.launch {
            val result = PluginLoader.loadPlugin(jarPath)
            if (result.isSuccess) {
                val plugin = result.getOrThrow()
                plugin.initialize()
                loadedPlugins = PluginLoader.getPlugins()
                selectPlugin(plugin)
                notificationService.notify(title = "Success", message = "Plugin loaded successfully")
            } else {
                notificationService.notify(
                    title = "Error",
                    message = "Failed to load plugin: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun unloadPlugin() {
        viewModelScope.launch {
            PluginLoader.unloadPlugin(jarPath)
            loadedPlugins = PluginLoader.getPlugins()
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

            val jobId = jobCounterMutex.withLock { ++jobCounter }.toString()
            val job = BackgroundJob(
                id = jobId,
                name = "${plugin.getManifest().plugin.name}: ${capability.name}",
                type = JobType.Capability,
                pluginId = plugin.getManifest().plugin.id,
                capabilityName = capability.name,
                parameters = params,
                keepResult = saveResults
            )
            jobManager.enqueueJob(job)

            notificationService.toast(
                message = "Executing ${capability.name} in background"
            )
        }
    }

    fun removeJob(jobId: String) {
        jobManager.removeJobs { it.id == jobId }
    }

    fun clearCapabilityHistory() {
        val pluginId = selectedPlugin?.getManifest()?.plugin?.id
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

            is DataType.Enum -> {
                JsonPrimitive(value)
            }
        }
    }
}
