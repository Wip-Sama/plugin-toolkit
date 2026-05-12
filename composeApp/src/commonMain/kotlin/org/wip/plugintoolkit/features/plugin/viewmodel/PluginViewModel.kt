package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore

class PluginViewModel(
    private val jobManager: JobManager,
    private val notificationService: NotificationService,
    private val pluginManager: PluginManager
) : ViewModel() {
    var jarPath by mutableStateOf("")
    var selectedPlugin by mutableStateOf<PluginEntry?>(null)
    var selectedCapability by mutableStateOf<Capability?>(null)
    var loadedPlugins by mutableStateOf(PluginLoader.getPlugins())

    var saveResults by mutableStateOf(true)
    val activeJobs = jobManager.jobs // Flow<List<BackgroundJob>>
    val endedJobs = jobManager.endedJobs // Flow<List<BackgroundJob>>
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
            .onEach { activeIds ->
                loadedPlugins = PluginLoader.getPlugins().filter {
                    try {
                        activeIds.contains(it.getManifest().plugin.id)
                    } catch (t: Throwable) {
                        Logger.e(t) { "Failed to get manifest during VM sync" }
                        false
                    }
                }
                val selectedId = try {
                    selectedPlugin?.getManifest()?.plugin?.id
                } catch (t: Throwable) {
                    //TODO: maybe do something
                    null
                }
                if (selectedPlugin != null && (selectedId == null || !activeIds.contains(selectedId))) {
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

        val pkg = try {
            selectedPlugin?.getManifest()?.plugin?.id ?: ""
        } catch (t: Throwable) {
            ""
        }
        val store = if (pkg.isNotEmpty()) pluginManager.loadPluginSettings(pkg) else null

        capability?.parameters?.forEach { (name, meta) ->
            // Priority: User Capability Default -> User Global Default -> Manifest Default
            val userValue = store?.capabilityParams?.get(capability.name)?.get(name)
                ?: store?.globalParams?.get(name)
                ?: meta.defaultValue

            parameterValues[name] = userValue?.let {
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
                try {
                    val pkg = plugin.getManifest().plugin.id
                    plugin.initialize(pluginManager.createPluginContext(pkg))
                    loadedPlugins = PluginLoader.getPlugins()
                    selectPlugin(plugin)
                    notificationService.notify(title = "Success", message = "Plugin loaded successfully")
                } catch (t: Throwable) {
                    val errorMsg = "Fatal error after loading plugin: ${t.message}"
                    Logger.e(t) { errorMsg }
                    notificationService.notify(title = "Error", message = errorMsg)
                }
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
            
            val manifest = try {
                plugin.getManifest()
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to get manifest for job creation" }
                null
            }

            val job = BackgroundJob(
                id = jobId,
                name = "${manifest?.plugin?.name ?: "Unknown"}: ${capability.name}",
                type = JobType.Capability,
                pluginId = manifest?.plugin?.id ?: "",
                capabilityName = capability.name,
                parameters = params,
                keepResult = saveResults,
                isPausable = capability.isPausable,
                isCancellable = capability.isCancellable
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

    fun removeEndedJob(jobId: String) {
        jobManager.clearEndedJob(jobId)
    }

    fun clearCapabilityHistory() {
        val pluginId = try {
            selectedPlugin?.getManifest()?.plugin?.id
        } catch (t: Throwable) {
            null
        }
        val capName = selectedCapability?.name
        if (pluginId != null && capName != null) {
            jobManager.removeJobs {
                it.pluginId == pluginId &&
                        it.capabilityName == capName &&
                        (it.status == JobStatus.Completed || it.status == JobStatus.Failed || it.status == JobStatus.Cancelled)
            }
            // Also clear from endedJobs if any
            jobManager.endedJobs.value.filter { it.pluginId == pluginId && it.capabilityName == capName }.forEach {
                jobManager.clearEndedJob(it.id)
            }
        }
    }

    private fun buildRequest(capability: Capability, values: Map<String, String>): PluginRequest {
        val params = mutableMapOf<String, JsonElement>()
        capability.parameters?.forEach { (name, meta) ->
            val stringValue = values[name] ?: ""
            params[name] = parseValue(stringValue, meta.type)
        }
        return PluginRequest(
            method = capability.name,
            parameters = params
        )
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
                    Logger.e("Failed to parse JSON", throwable = e)
                    JsonPrimitive(value)
                }
            }

            is DataType.Enum -> {
                JsonPrimitive(value)
            }
        }
    }
}
