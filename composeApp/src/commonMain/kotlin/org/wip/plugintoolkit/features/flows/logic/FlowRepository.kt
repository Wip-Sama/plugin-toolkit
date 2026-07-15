package org.wip.plugintoolkit.features.flows.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.MigrationEngine
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence

class FlowRepository(
    private val settingsPersistence: SettingsPersistence,
    private val pluginManager: PluginManager,
    private val scope: CoroutineScope,
    private val appConfig: SystemConfig
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _flows = MutableStateFlow<List<Flow>>(emptyList())
    val flows: StateFlow<List<Flow>> = _flows.asStateFlow()

    init {
        reloadFlows()
        scope.launch {
            pluginManager.installedPlugins.collect {
                reloadFlows()
            }
        }
    }

    private fun getFlowPath(appDataDir: String, flowName: String): Path {
        val safeName = flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Path("$appDataDir/flows/$safeName.json")
    }

    fun reloadFlows() {
        scope.launch(Dispatchers.IO) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                val flowsDir = Path("$appDataDir/flows")

                if (!SystemFileSystem.exists(flowsDir)) {
                    SystemFileSystem.createDirectories(flowsDir)
                }

                // Check for legacy migration first
                val legacyFile = Path("$appDataDir/${appConfig.FLOWS_FILE_NAME}")
                if (SystemFileSystem.exists(legacyFile)) {
                    try {
                        val legacyContent = SystemFileSystem.source(legacyFile).buffered().use { it.readString() }
                        if (legacyContent.isNotBlank()) {
                            val loadedFlows = json.decodeFromString<List<Flow>>(legacyContent)
                            loadedFlows.forEach { flow ->
                                val targetFile = getFlowPath(appDataDir, flow.name)
                                if (!SystemFileSystem.exists(targetFile)) {
                                    val flowContent = json.encodeToString(Flow.serializer(), flow)
                                    SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
                                }
                            }
                        }
                        val backupFile = Path("$appDataDir/${appConfig.FLOWS_FILE_NAME}.bak")
                        if (SystemFileSystem.exists(backupFile)) {
                            SystemFileSystem.delete(backupFile)
                        }
                        SystemFileSystem.source(legacyFile).buffered().use { source ->
                            SystemFileSystem.sink(backupFile).buffered().use { sink ->
                                val data = source.readString()
                                sink.writeString(data)
                            }
                        }
                        SystemFileSystem.delete(legacyFile)
                        Logger.i { "Legacy flows.json successfully migrated and backed up" }
                    } catch (e: Exception) {
                        Logger.e(e) { "Migration failed" }
                    }
                }

                val loadedFlows = mutableListOf<Flow>()
                val manifests = pluginManager.installedPlugins.value.filter { it.isEnabled }
                    .associate { it.pkg to pluginManager.getManifest(it.pkg) }
                    .filterValues { it != null }.mapValues { it.value!! }

                SystemFileSystem.list(flowsDir).forEach { file ->
                    if (file.name.endsWith(".json")) {
                        try {
                            val content = SystemFileSystem.source(file).buffered().use { it.readString() }
                            val flow = json.decodeFromString<Flow>(content)

                            val updatedNodes = flow.nodes.map { node ->
                                if (node is Node.CapabilityNode) {
                                    val currentManifest = manifests[node.pluginInfo.id]
                                    val actualCapability =
                                        currentManifest?.capabilities?.find { it.name == node.capability.name }
                                    if (currentManifest == null || actualCapability == null) {
                                        node.copy(isBroken = true)
                                    } else {
                                        node.copy(
                                            isBroken = false,
                                            capability = actualCapability,
                                            pluginInfo = currentManifest.plugin
                                        )
                                    }
                                } else {
                                    node
                                }
                            }
                            loadedFlows.add(flow.copy(nodes = updatedNodes))
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse flow file: ${file.name}" }
                        }
                    }
                }

                _flows.value = loadedFlows
            } catch (e: Exception) {
                Logger.e(e) { "Failed to reload flows" }
            }
        }
    }

    fun saveFlow(flow: Flow) {
        scope.launch(Dispatchers.IO) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                val flowsDir = Path("$appDataDir/flows")
                if (!SystemFileSystem.exists(flowsDir)) {
                    SystemFileSystem.createDirectories(flowsDir)
                }

                val file = getFlowPath(appDataDir, flow.name)
                val flowContent = json.encodeToString(Flow.serializer(), flow)
                SystemFileSystem.sink(file).buffered().use { it.writeString(flowContent) }

                _flows.update { current ->
                    val existing = current.find { it.name == flow.name }
                    if (existing != null) {
                        current.map { if (it.name == flow.name) flow else it }
                    } else {
                        current + flow
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to save flow: ${flow.name}" }
            }
        }
    }

    fun deleteFlow(flowName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                val file = getFlowPath(appDataDir, flowName)

                val currentFlows = _flows.value
                val deletedFlow = currentFlows.find { it.name == flowName }

                if (deletedFlow != null) {
                    val updatedList = currentFlows.map { parentFlow ->
                        if (parentFlow.name == flowName) return@map parentFlow
                        var updatedFlow = parentFlow
                        var foundSubflowNode: Node.SubFlowNode?
                        do {
                            foundSubflowNode =
                                updatedFlow.nodes.find { it is Node.SubFlowNode && it.flowName == flowName } as? Node.SubFlowNode
                            if (foundSubflowNode != null) {
                                updatedFlow =
                                    org.wip.plugintoolkit.features.flows.model.FlowUnpacker.unpackSubflowInFlow(
                                        updatedFlow,
                                        foundSubflowNode.id,
                                        deletedFlow
                                    )
                            }
                        } while (foundSubflowNode != null)

                        if (updatedFlow != parentFlow) {
                            val targetFile = getFlowPath(appDataDir, updatedFlow.name)
                            val flowContent = json.encodeToString(Flow.serializer(), updatedFlow)
                            SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
                        }
                        updatedFlow
                    }.filter { it.name != flowName }
                    _flows.value = updatedList
                }

                if (SystemFileSystem.exists(file)) {
                    SystemFileSystem.delete(file)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to delete flow: $flowName" }
            }
        }
    }

    fun renameFlow(oldName: String, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                val oldFile = getFlowPath(appDataDir, oldName)
                val newFile = getFlowPath(appDataDir, newName)

                val flow = _flows.value.find { it.name == oldName } ?: return@launch
                val updatedFlow = flow.copy(name = newName)

                if (SystemFileSystem.exists(oldFile)) {
                    SystemFileSystem.delete(oldFile)
                }

                val flowContent = json.encodeToString(Flow.serializer(), updatedFlow)
                SystemFileSystem.sink(newFile).buffered().use { it.writeString(flowContent) }

                _flows.update { current ->
                    current.filter { it.name != oldName } + updatedFlow
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to rename flow from $oldName to $newName" }
            }
        }
    }

    fun updateFlowMetadata(flowName: String, newVersion: String, newDescription: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                val flowFile = getFlowPath(appDataDir, flowName)

                val flow = _flows.value.find { it.name == flowName } ?: return@launch
                val updatedFlow = flow.copy(version = newVersion, description = newDescription)

                val flowContent = json.encodeToString(Flow.serializer(), updatedFlow)
                SystemFileSystem.sink(flowFile).buffered().use { it.writeString(flowContent) }

                _flows.update { current ->
                    current.map { if (it.name == flowName) updatedFlow else it }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to update flow metadata for $flowName" }
            }
        }
    }

    fun triggerMigrationsForUpdatedPlugin(pluginId: String) {
        scope.launch(Dispatchers.IO) {
            val appDataDir = settingsPersistence.getSettingsDir()
            val manifests =
                pluginManager.installedPlugins.value.associate { it.pkg to pluginManager.getManifest(it.pkg) }
                    .filterValues { it != null }.mapValues { it.value!! }

            var flowsChanged = false
            val updatedFlows = _flows.value.map { flow ->
                val usesPlugin = flow.nodes.any { it is Node.CapabilityNode && it.pluginInfo.id == pluginId }
                if (usesPlugin) {
                    val migResult = MigrationEngine.migrateFlow(
                        flow = flow,
                        currentManifests = manifests,
                        getMigrations = { pluginManager.getMigrations(it) }
                    )

                    if (migResult.migratedFlow != flow) {
                        val file = getFlowPath(appDataDir, flow.name)
                        val flowContent = json.encodeToString(Flow.serializer(), migResult.migratedFlow)
                        SystemFileSystem.sink(file).buffered().use { it.writeString(flowContent) }
                        flowsChanged = true
                        migResult.migratedFlow
                    } else {
                        flow
                    }
                } else {
                    flow
                }
            }

            if (flowsChanged) {
                _flows.value = updatedFlows
            }
        }
    }
}
