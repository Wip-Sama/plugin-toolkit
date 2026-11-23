package org.wip.complete

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.HttpResponse
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.PluginNotificationType
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSecretStorage
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.SearchBarNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.ShapeRole
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.UiShortcut
import org.wip.plugintoolkit.api.sdui.VectorPathData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompleteExampleTest {

    private class FakePluginLogger : PluginLogger {
        val logs = mutableListOf<String>()
        override fun verbose(message: String) { logs.add("VERBOSE: $message") }
        override fun debug(message: String) { logs.add("DEBUG: $message") }
        override fun info(message: String) { logs.add("INFO: $message") }
        override fun warn(message: String) { logs.add("WARN: $message") }
        override fun error(message: String, throwable: Throwable?) { logs.add("ERROR: $message") }
    }

    private class FakeProgressReporter(
        private val onPrimary: (ProgressData) -> Unit = {},
        private val onSecondary: (ProgressData?) -> Unit = {}
    ) : ProgressReporter {
        var lastProgress: Float = 0f
        override fun report(progress: Float) {
            lastProgress = progress
            onPrimary(ProgressData.percentage(progress))
        }
        override fun report(data: ProgressData) {
            data.fraction?.let { lastProgress = it }
            onPrimary(data)
        }
        override fun reportSecondary(progress: Float) {
            onSecondary(ProgressData.percentage(progress))
        }
        override fun reportSecondary(data: ProgressData) {
            onSecondary(data)
        }
        override fun clearSecondary() {
            onSecondary(null)
        }
    }

    private class FakePluginSignalManager : PluginSignalManager {
        private val handlers = mutableListOf<suspend (PluginSignal) -> Unit>()
        override fun onSignal(handler: suspend (PluginSignal) -> Unit) { handlers.add(handler) }
        override suspend fun sendSignal(signal: PluginSignal) { handlers.forEach { it(signal) } }
    }

    private class FakePluginStorage : PluginStorage {
        val data = mutableMapOf<String, JsonElement>()
        override suspend fun get(key: String): JsonElement? = data[key]
        override suspend fun put(key: String, value: JsonElement) { data[key] = value }
        override suspend fun getAll(): Map<String, JsonElement> = data
        override suspend fun remove(key: String) { data.remove(key) }
    }

    private class FakePluginFileSystem : PluginFileSystem {
        val files = mutableMapOf<String, ByteArray>()
        override suspend fun readFile(relativePath: RelativePath): ByteArray? = files[relativePath.value]
        override suspend fun readTextFile(relativePath: RelativePath): String? = files[relativePath.value]?.decodeToString()
        override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
            files[relativePath.value] = data
            return Result.success(Unit)
        }
        override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> {
            files[relativePath.value] = text.encodeToByteArray()
            return Result.success(Unit)
        }
        override suspend fun exists(relativePath: RelativePath): Boolean = relativePath.value in files
        override suspend fun listFiles(relativePath: RelativePath): List<String> = files.keys.filter { it.startsWith(relativePath.value) }
        override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> {
            files.remove(relativePath.value)
            return Result.success(Unit)
        }
        override fun getBasePath(): String = "/virtual-plugin"
        override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> = Result.success(Unit)
    }

    private class FakeSecretStorage : PluginSecretStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun removeSecret(key: String) { values.remove(key) }
        override suspend fun listSecretKeys(): List<String> = values.keys.toList()
    }

    private class FakeNetworkClient : PluginNetworkClient {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun get(url: String, headers: Map<String, String>): ByteArray = "OK".encodeToByteArray()
        override suspend fun getText(url: String, headers: Map<String, String>): String = "OK"
        override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray = "OK".encodeToByteArray()
        override suspend fun postText(url: String, body: String, headers: Map<String, String>): String = "OK"
        override suspend fun request(request: HttpRequest): HttpResponse {
            requests.add(request)
            return HttpResponse(statusCode = 200, body = "OK".encodeToByteArray())
        }
    }

    private class TestPluginContext(
        override val logger: PluginLogger = FakePluginLogger(),
        override val progress: ProgressReporter = FakeProgressReporter(),
        override val signals: PluginSignalManager = FakePluginSignalManager(),
        override val storage: PluginStorage = FakePluginStorage(),
        override val fileSystem: PluginFileSystem = FakePluginFileSystem(),
        val secretStorage: FakeSecretStorage = FakeSecretStorage(),
        val fakeNetworkClient: FakeNetworkClient = FakeNetworkClient(),
        initialSettings: Map<String, JsonElement> = emptyMap(),
        private val onSettingsMutated: (Map<String, JsonElement>) -> Unit = {}
    ) : PluginContext {
        val updatedSettings = mutableMapOf<String, JsonElement>().apply { putAll(initialSettings) }
        val notifications = mutableListOf<Triple<String, String, PluginNotificationType>>()
        override val settings: Map<String, JsonElement> get() = updatedSettings
        override val secrets: PluginSecretStorage get() = secretStorage
        override val networkClient: PluginNetworkClient get() = fakeNetworkClient

        override val cacheFileSystem: PluginFileSystem get() = fileSystem
        override val executionFileSystem: ExecutionFileSystem get() = throw UnsupportedOperationException()
        override val hostFileSystem: HostFileSystem get() = throw UnsupportedOperationException()

        override fun setRequiredAction(actionName: String?) {}

        override fun showNotification(title: String, message: String, type: PluginNotificationType) {
            notifications.add(Triple(title, message, type))
        }

        override suspend fun updateSetting(key: String, value: JsonElement) {
            updatedSettings[key] = value
            onSettingsMutated(updatedSettings.toMap())
        }

        override suspend fun clearSetting(key: String) {
            updatedSettings.remove(key)
            onSettingsMutated(updatedSettings.toMap())
        }
    }

    private class TestWiredSessionContext(
        override val capabilityName: String,
        private val entry: PluginEntry,
        private val scope: CoroutineScope,
        private val baseContext: TestPluginContext,
        private val settingsState: MutableStateFlow<Map<String, JsonElement>>,
        private val locksState: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap()),
        override val initialParameters: Map<String, JsonElement> = emptyMap(),
        private val fakePickedFiles: List<String> = emptyList(),
        private val fakePickedDirectory: String? = null
    ) : UiSessionContext, PluginContext by baseContext {
        private val jobsState = MutableStateFlow<List<CapabilityJobSnapshot>>(emptyList())
        private var jobCounter = 0
        val savedFiles = mutableListOf<Pair<String, Int>>()
        val openedTools = mutableListOf<Pair<String, Map<String, JsonElement>>>()

        override val sessionId: String = "test_session_$capabilityName"
        override val settingsFlow: StateFlow<Map<String, JsonElement>> = settingsState.asStateFlow()
        override val locksFlow: StateFlow<Map<String, Boolean>> = locksState.asStateFlow()
        override val capabilityJobs: StateFlow<List<CapabilityJobSnapshot>> = jobsState.asStateFlow()

        override fun completeSession(response: PluginResponse) {}
        override fun cancelSession(reason: String) {}

        override suspend fun pickFiles(title: String?, allowedExtensions: List<String>): List<String> = fakePickedFiles

        override suspend fun pickDirectory(title: String?): String? = fakePickedDirectory

        override suspend fun saveFile(defaultName: String, extension: String, bytes: ByteArray): String? {
            val outPath = "/tmp/$defaultName"
            savedFiles.add(outPath to bytes.size)
            return outPath
        }

        override fun openInteractiveTool(capabilityName: String, initialParameters: Map<String, JsonElement>) {
            openedTools.add(capabilityName to initialParameters)
        }

        override suspend fun updateSetting(key: String, value: JsonElement) {
            baseContext.updateSetting(key, value)
            settingsState.value = baseContext.settings.toMap()
        }

        override suspend fun clearSetting(key: String) {
            baseContext.clearSetting(key)
            settingsState.value = baseContext.settings.toMap()
        }

        override suspend fun launchCapability(
            capabilityName: String,
            parameters: Map<String, JsonElement>,
            runInBackgroundQueue: Boolean
        ): String {
            val id = "job_${++jobCounter}"
            jobsState.update { it + CapabilityJobSnapshot(id, capabilityName, CapabilityJobStatus.RUNNING) }
            scope.launch {
                runJob(id, capabilityName, parameters)
            }
            return id
        }

        override suspend fun executeCapability(
            capabilityName: String,
            parameters: Map<String, JsonElement>
        ): ExecutionResult {
            val id = "job_${++jobCounter}"
            jobsState.update { it + CapabilityJobSnapshot(id, capabilityName, CapabilityJobStatus.RUNNING) }
            return runJob(id, capabilityName, parameters)
        }

        override suspend fun launchAction(
            actionName: String,
            parameters: Map<String, JsonElement>
        ): Result<Unit> {
            val proc = entry.getProcessor().getOrThrow()
            val actionMeta = entry.getManifest().getOrThrow().actions.find {
                it.functionName.equals(actionName, ignoreCase = true)
            } ?: PluginAction(name = actionName, description = actionName, functionName = actionName)
            val result = proc.runAction(actionMeta, parameters, this)
            settingsState.value = baseContext.settings.toMap()
            locksState.value = proc.refreshLocks(this)
            return result
        }

        private suspend fun runJob(
            jobId: String,
            capabilityName: String,
            parameters: Map<String, JsonElement>
        ): ExecutionResult {
            val proc = entry.getProcessor().getOrThrow()
            val jobContext = object : PluginContext by this {
                override val progress: ProgressReporter = FakeProgressReporter(
                    onPrimary = { data ->
                        jobsState.update { list ->
                            list.map { if (it.jobId == jobId) it.copy(primaryProgress = data) else it }
                        }
                    },
                    onSecondary = { data ->
                        jobsState.update { list ->
                            list.map { if (it.jobId == jobId) it.copy(secondaryProgress = data) else it }
                        }
                    }
                )
            }
            val result = proc.process(PluginRequest(method = capabilityName, parameters = parameters), jobContext)
            val status = when (result) {
                is ExecutionResult.Success -> CapabilityJobStatus.COMPLETED
                is ExecutionResult.Paused -> CapabilityJobStatus.PAUSED
                is ExecutionResult.Error -> CapabilityJobStatus.FAILED
            }
            jobsState.update { list ->
                list.map {
                    if (it.jobId == jobId) {
                        it.copy(
                            status = status,
                            result = result,
                            errorMessage = (result as? ExecutionResult.Error)?.message
                        )
                    } else it
                }
            }
            return result
        }
    }

    private fun collectNodes(node: UiNode): List<UiNode> {
        val result = mutableListOf(node)
        when (node) {
            is ColumnNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.RowNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.BoxNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.CardNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.ScrollableColumnNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.ScrollableRowNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is GridNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.SidebarNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.FloatingPanelNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is DialogNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is AccordionNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.ListItemNode -> node.trailingContent.forEach { result.addAll(collectNodes(it)) }
            is SplitPaneNode -> {
                result.addAll(collectNodes(node.first))
                result.addAll(collectNodes(node.second))
            }
            is TabsNode -> result.addAll(collectNodes(node.content))
            is ScaffoldNode -> {
                node.topBar?.let { result.addAll(collectNodes(it)) }
                node.bottomBar?.let { result.addAll(collectNodes(it)) }
                node.leftSidebar?.let { result.addAll(collectNodes(it)) }
                node.rightSidebar?.let { result.addAll(collectNodes(it)) }
                result.addAll(collectNodes(node.content))
                node.floatingPanels.forEach { result.addAll(collectNodes(it)) }
            }
            is BoardNode -> {
                node.layers.filterIsInstance<BoardLayer.ComponentLayer>().forEach { layer ->
                    result.addAll(collectNodes(layer.content))
                }
            }
            else -> Unit
        }
        return result
    }

    @Test
    fun testLifecycleHooks() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val logger = FakePluginLogger()
        val context = TestPluginContext(logger = logger)

        assertTrue(plugin.onLoad(logger).isSuccess)
        assertTrue(plugin.validate(logger, context).isSuccess)
        assertTrue(plugin.onUpdate(logger, context).isSuccess)
    }

    @Test
    fun testPluginValidationFailure() {
        val settings = CompleteExampleSettings(secretToken = "")
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()
        val result = plugin.validate(context.logger, context)
        assertTrue(result.isFailure)
    }

    @Test
    fun testCapabilityWithPauseResume() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val signals = FakePluginSignalManager()
        val context = TestPluginContext(signals = signals)

        val signalJob = launch {
            delay(20)
            signals.sendSignal(PluginSignal.PAUSE)
        }

        val result = plugin.capabilityWithPauseResume(
            totalSteps = 5,
            stepDelayMs = 50,
            resumeState = null,
            context = context
        )

        signalJob.join()
        assertTrue(result is ExecutionResult.Paused, "Expected result to be ExecutionResult.Paused but was $result")
    }

    @Test
    fun testCapabilityWithFileAccess() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val tempInput = File.createTempFile("test_input", ".txt")
        val tempOutput = File.createTempFile("test_output", ".txt")
        tempInput.writeText("hello world")

        try {
            val result = plugin.capabilityWithFileAccess(tempInput.absolutePath, tempOutput.absolutePath)
            assertEquals(200, result.status)
            assertTrue(tempOutput.readText().contains("HELLO WORLD"))
        } finally {
            tempInput.delete()
            tempOutput.delete()
        }
    }

    @Test
    fun testCapabilityWithFileDefaults() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)

        val result = plugin.capabilityWithFileDefaults()
        assertTrue(result.contains("default_input.txt"))
        assertTrue(result.contains("default_output.txt"))
    }

    @Test
    fun testCapabilityWithComplexObjectsAndSemanticTypes() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val inputPacket = UserDataPacket("pkt_001", 85.0, listOf("v1", "test"))

        val paletteResult = plugin.capabilityWithComplexObjectsAndSemanticTypes(
            packet = inputPacket,
            baseColor = "rgb(100, 150, 200)"
        )

        assertEquals("rgb(100, 150, 200)", paletteResult.primaryColor)
        assertEquals("rgb(255, 255, 255)", paletteResult.accentColor)
        assertEquals(95.0, paletteResult.packet.score)
    }

    @Test
    fun testCapabilityWithDataStorage() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        val response = plugin.capabilityWithDataStorage("user_theme", "dark", context)
        assertTrue(response.contains("dark"))
    }

    @Test
    fun testCapabilityWithFlowContext() {
        val settings = CompleteExampleSettings(userId = "user_123")
        val plugin = CompleteExamplePlugin(settings)
        val response = plugin.capabilityWithFlowContext(
            mode = FeatureMode.LOCAL,
            config = mapOf("key" to "value")
        )
        assertTrue(response.contains("LOCAL"))
        assertTrue(response.contains("user_123"))
    }

    @Test
    fun testManifestSettingsRequiredFlag() {
        val manifestStream = javaClass.classLoader.getResourceAsStream("META-INF/manifest.json")
        kotlin.test.assertNotNull(manifestStream, "META-INF/manifest.json resource should exist")
        val content = manifestStream.bufferedReader().readText()
        val jsonElement = Json.parseToJsonElement(content)
        val settingsObj = jsonElement.jsonObject["settings"]?.jsonObject
        val secretTokenObj = settingsObj?.get("secretToken")?.jsonObject
        val isRequired = secretTokenObj?.get("required")?.jsonPrimitive?.booleanOrNull
        assertEquals(true, isRequired, "secretToken should have required=true in manifest.json")
    }

    @Test
    fun testToggleFeatureLockActionAndCausality() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        val initialLocks = plugin.checkLocks(context)
        assertEquals(false, initialLocks["feature_unlocked"])

        plugin.toggleFeatureLock(unlocked = true, context = context)

        val updatedLocks = plugin.checkLocks(context)
        assertEquals(true, updatedLocks["feature_unlocked"])

        val result = plugin.capabilityWithLockRequirement(feature = RestrictedFeatureEnum.STANDARD, context = context)
        assertTrue(result.startsWith("Feature is unlocked and operational!"))

        plugin.toggleFeatureLock(unlocked = false, context = context)
        val relockedLocks = plugin.checkLocks(context)
        assertEquals(false, relockedLocks["feature_unlocked"])
    }

    @Test
    fun testUpdateApiKeySettingAction() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        plugin.updateApiKeySetting(newApiKey = "custom-api-key-999", context = context)
        assertEquals("custom-api-key-999", context.getStringSetting("apiKey"))
    }

    @Test
    fun testCapabilityWithErrorDetail() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)

        val errorResult = plugin.capabilityWithErrorDetail(triggerError = true)
        assertTrue(errorResult is ExecutionResult.Error)
        assertEquals("Simulated domain error with structured diagnostic details", errorResult.error.message)
        assertEquals("DomainValidationException", errorResult.error.exceptionType)
        assertEquals("ERR_DEMO_001", errorResult.error.details["errorCode"])

        val successResult = plugin.capabilityWithErrorDetail(triggerError = false)
        assertTrue(successResult is ExecutionResult.Success)
    }

    @Test
    fun testCapabilityWithProcessWatcher() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        val result = plugin.capabilityWithProcessWatcher("test-param", context)
        assertTrue(result.contains("Watched process PID"))
    }

    @Test
    fun testCapabilityWithDynamicAndAdvancedParams() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)

        val defaultResult = plugin.capabilityWithDynamicAndAdvancedParams()
        assertTrue(defaultResult.contains("FAST_INFERENCE"))

        val customResult = plugin.capabilityWithDynamicAndAdvancedParams(
            modelArchitecture = AIModelArchitecture.CUSTOM_CHECKPOINT,
            customWeightsPath = "path/to/weights.bin",
            batchSize = 32,
            quantizationBits = 4,
            remoteEndpoint = "https://api.example.com",
            debugLogPath = "logs/debug.txt"
        )
        assertTrue(customResult.contains("CUSTOM_CHECKPOINT"))
        assertTrue(customResult.contains("path/to/weights.bin"))
        assertTrue(customResult.contains("32"))
    }

    @Test
    fun testCapabilityWithParameterGroup() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)

        val defaultResult = plugin.capabilityWithParameterGroup()
        assertTrue(defaultResult.contains("Engine: LOCAL_LLM"))
        assertTrue(defaultResult.contains("Temp: 0.7"))
        assertTrue(defaultResult.contains("MaxTokens: 1024"))
        assertTrue(defaultResult.contains("Filter: false"))

        val customResult = plugin.capabilityWithParameterGroup(
            engine = "LOCAL_LLM",
            inferenceOptions = ModelInferenceOptions(
                temperature = 0.2,
                maxTokens = 2048,
                postProcessing = PostProcessingOptions(
                    applyFilter = true,
                    filterIntensity = 0.95
                )
            )
        )
        assertTrue(customResult.contains("Temp: 0.2"))
        assertTrue(customResult.contains("MaxTokens: 2048"))
        assertTrue(customResult.contains("Filter: true"))
        assertTrue(customResult.contains("Intensity: 0.95"))
    }

    @Test
    fun testDispatcherWithUnpackedParameterGroup() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val dispatcher = CompleteExamplePluginDispatcher(plugin)
        val context = TestPluginContext()

        val request = org.wip.plugintoolkit.api.PluginRequest(
            method = "capabilitywithparametergroup",
            parameters = mapOf(
                "engine" to kotlinx.serialization.json.JsonPrimitive("LOCAL_LLM"),
                "llm_temperature" to kotlinx.serialization.json.JsonPrimitive(0.3),
                "llm_maxTokens" to kotlinx.serialization.json.JsonPrimitive(512),
                "llm_post_applyFilter" to kotlinx.serialization.json.JsonPrimitive(true),
                "llm_post_filterIntensity" to kotlinx.serialization.json.JsonPrimitive(0.9)
            )
        )

        val response = dispatcher.process(request, context)
        assertTrue(response is ExecutionResult.Success)
        val output = (response as ExecutionResult.Success).response.result?.jsonPrimitive?.content ?: ""
        assertTrue(output.contains("Temp: 0.3"))
        assertTrue(output.contains("MaxTokens: 512"))
        assertTrue(output.contains("Filter: true"))
        assertTrue(output.contains("Intensity: 0.9"))
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun testInteractiveBoardCompositorSessionLifecycleAndExport() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val entry = CompleteExamplePluginPluginEntry(plugin)

        val interactiveProcessor = entry.getInteractiveProcessor()?.getOrNull()
        assertNotNull(interactiveProcessor, "InteractiveUiProcessor must be non-null in generated entry")

        val eventsFlow = MutableSharedFlow<UiEvent>()
        val emittedTrees = mutableListOf<UiNodeTree>()
        val fakeFs = FakePluginFileSystem()
        val testContext = TestPluginContext(fileSystem = fakeFs)
        val settingsState = MutableStateFlow<Map<String, JsonElement>>(emptyMap())

        val collectScope = kotlinx.coroutines.CoroutineScope(coroutineContext)
        val sessionContext = TestWiredSessionContext(
            capabilityName = "interactiveBoardCompositor",
            entry = entry,
            scope = collectScope,
            baseContext = testContext,
            settingsState = settingsState
        )

        val job = launch {
            interactiveProcessor.openUiSession(sessionContext, eventsFlow).collect { tree ->
                emittedTrees.add(tree)
            }
        }
        try {
            advanceUntilIdle()

        assertTrue(emittedTrees.isNotEmpty(), "Expected initial compositor tree")
        val initialTree = emittedTrees.last()
        val serializedTree = Json.encodeToString(UiNodeTree.serializer(), initialTree)
        val roundTripped = Json.decodeFromString(UiNodeTree.serializer(), serializedTree)
        assertTrue(roundTripped.root is ScaffoldNode)

        val scaffold = initialTree.root as ScaffoldNode
        val board = scaffold.content as BoardNode
        assertNotNull(board.backgroundAsset, "Storyboard page backgroundAsset must be generated")
        assertTrue(board.layers.any { it is BoardLayer.ImageLayer && it.id == "stamp_img_1" })
        assertTrue(board.layers.any { it is BoardLayer.TextLayer && it.id == "text_1" })
        assertTrue(board.layers.any { it is BoardLayer.ShapeLayer && it.id == "callout_1" })
        assertTrue(board.layers.any { it is BoardLayer.ComponentLayer && it.id == "inspector_widget" })
        assertEquals(2, scaffold.floatingPanels.size)
        assertEquals("controlsPanel", scaffold.floatingPanels.first().id)
        assertEquals(FloatingAnchor.TOP_END, scaffold.floatingPanels.first().anchor)
        assertTrue(
            scaffold.floatingPanels.first().isCollapsed,
            "Board controls floating panel must start collapsed on startup so it does not cover the workspace"
        )
        assertEquals("quickStatusPill", scaffold.floatingPanels[1].id)
        assertEquals(FloatingAnchor.BOTTOM_START, scaffold.floatingPanels[1].anchor)
        assertTrue(scaffold.floatingPanels[1].compact)
        assertNotNull(scaffold.rightSidebar, "Compositor must expose layer inspector rightSidebar")
        assertNotNull(scaffold.topBar, "Compositor must expose topBar actions")

        // Assert globally unique node IDs inside the compositor tree
        val allIds = collectNodes(initialTree.root).map { it.id }
        assertEquals(allIds.size, allIds.toSet().size, "All node IDs in compositor tree must be unique")

        // Inline text edit on TextLayer + layer move + title change
        eventsFlow.emit(UiEvent.BoardTextEdited("drawingBoard", "text_1", "Updated Dialogue Caption"))
        eventsFlow.emit(UiEvent.BoardLayerMoved("drawingBoard", "text_1", NormalizedPoint(0.25f, 0.45f)))
        eventsFlow.emit(UiEvent.TextChanged("titleInput", "Chapter 42 — Reviewed"))
        advanceUntilIdle()

        val editedBoard = (emittedTrees.last().root as ScaffoldNode).content as BoardNode
        val editedTextLayer = editedBoard.layers.filterIsInstance<BoardLayer.TextLayer>().first { it.id == "text_1" }
        assertEquals("Updated Dialogue Caption", editedTextLayer.text)
        assertEquals(NormalizedPoint(0.25f, 0.45f), editedTextLayer.position)

        // Dispatch path committed, tool change, color change
        val testPath = VectorPathData(
            strokeId = "test_stroke",
            points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.5f, 0.5f)),
            color = SemanticColor.PRIMARY,
            strokeWidth = 4f
        )
        eventsFlow.emit(UiEvent.PathCommitted("drawingBoard", testPath))
        eventsFlow.emit(UiEvent.OptionSelected("toolDropdown", "HIGHLIGHTER"))
        eventsFlow.emit(UiEvent.OptionSelected("colorDropdown", "HIGHLIGHT"))
        advanceUntilIdle()

        val colorBoard = (emittedTrees.last().root as ScaffoldNode).content as BoardNode
        assertTrue(colorBoard.layers.any { it is BoardLayer.PathLayer })
        assertTrue(colorBoard.activeTool is BoardTool.Highlighter)
        assertEquals(SemanticColor.HIGHLIGHT, (colorBoard.activeTool as BoardTool.Highlighter).color)

        // Trigger renderCompositedPage export capability from the UI
        eventsFlow.emit(UiEvent.Click("saveRenderBtn", "export_page"))
        advanceUntilIdle()

        assertTrue(
            sessionContext.capabilityJobs.value.any {
                it.capabilityName == "renderCompositedPage" && it.status == CapabilityJobStatus.COMPLETED
            },
            "Expected renderCompositedPage job to complete"
        )
        assertTrue(fakeFs.files.containsKey(CompleteExampleSampleAssets.EXPORT_IMAGE_PATH))
        assertTrue(fakeFs.files.containsKey(CompleteExampleSampleAssets.EXPORT_REPORT_PATH))
        } finally {
            job.cancel()
        }
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun testInteractiveChapterPublisherSessionAndCapabilityBridge() = runTest {
        val settings = CompleteExampleSettings(userId = "manhwa_lead")
        val plugin = CompleteExamplePlugin(settings)
        val entry = CompleteExamplePluginPluginEntry(plugin)
        val interactiveProcessor = entry.getInteractiveProcessor()?.getOrNull()
        assertNotNull(interactiveProcessor)

        val eventsFlow = MutableSharedFlow<UiEvent>()
        val emittedTrees = mutableListOf<UiNodeTree>()
        val fakeFs = FakePluginFileSystem()
        val settingsState = MutableStateFlow<Map<String, JsonElement>>(
            mapOf(
                "userId" to JsonPrimitive("manhwa_lead"),
                "apiKey" to JsonPrimitive("")
            )
        )
        val testContext = TestPluginContext(
            fileSystem = fakeFs,
            initialSettings = settingsState.value
        ) { updated -> settingsState.value = updated }

        val locksState = MutableStateFlow(mapOf("feature_unlocked" to false))
        val sessionScope = CoroutineScope(coroutineContext)
        val sessionContext = TestWiredSessionContext(
            capabilityName = "interactiveChapterPublisher",
            entry = entry,
            scope = sessionScope,
            baseContext = testContext,
            settingsState = settingsState,
            locksState = locksState,
            fakePickedFiles = listOf("/tmp/picked_page_05.png"),
            fakePickedDirectory = "/tmp/local_cubari_repo"
        )

        val job = launch {
            interactiveProcessor.openUiSession(sessionContext, eventsFlow).collect { tree ->
                emittedTrees.add(tree)
            }
        }
        try {
            advanceUntilIdle()

        assertTrue(emittedTrees.isNotEmpty(), "Expected initial publisher tree")
        val initialTree = emittedTrees.last()
        val roundTripped = Json.decodeFromString(
            UiNodeTree.serializer(),
            Json.encodeToString(UiNodeTree.serializer(), initialTree)
        )
        assertTrue(roundTripped.root is ScaffoldNode)

        val initialNodes = collectNodes(initialTree.root)
        val initialAllIds = initialNodes.map { it.id }
        assertEquals(initialAllIds.size, initialAllIds.toSet().size, "Publisher tree node IDs must be unique")
        assertTrue(
            initialNodes.any { it.id == "pub_target_status_group" && it is org.wip.plugintoolkit.api.sdui.ButtonGroupNode },
            "Expected M3 Expressive ButtonGroupNode target status strip in topBar"
        )
        assertTrue(
            initialNodes.any { it.id == "pub_push_selected_btn" && it is org.wip.plugintoolkit.api.sdui.SplitButtonNode },
            "Expected M3 Expressive SplitButtonNode primary push action in topBar"
        )
        assertTrue("series_row_series_obsidian" in initialAllIds, "Expected initial Series view showing Obsidian series")
        assertTrue("series_row_series_crimson" in initialAllIds, "Expected Crimson series in Series view")
        assertTrue("series_row_series_astral" in initialAllIds, "Expected Astral series in Series view")

        // Exercise Series custom ordering & sorting, then drill into Obsidian chapters
        eventsFlow.emit(UiEvent.Click("series_down_btn_series_obsidian", "move_series_down:series_obsidian"))
        advanceUntilIdle()
        val reorderedSeriesRows = collectNodes(emittedTrees.last().root)
            .filterIsInstance<ListItemNode>()
            .filter { it.id.startsWith("series_row_") }
        assertEquals("series_row_series_crimson", reorderedSeriesRows.first().id)

        eventsFlow.emit(UiEvent.Click("series_up_btn_series_obsidian", "move_series_up:series_obsidian"))
        eventsFlow.emit(UiEvent.Click("series_open_btn_series_obsidian", "open_series:series_obsidian"))
        advanceUntilIdle()

        val chapterNodes = collectNodes(emittedTrees.last().root)
        val chapterAllIds = chapterNodes.map { it.id }
        assertEquals(chapterAllIds.size, chapterAllIds.toSet().size, "Chapters tree node IDs must be unique")
        assertTrue("pub_row_ch_041" in chapterAllIds, "Expected ch_041 in loaded series catalog")
        assertTrue("pub_row_ch_043" in chapterAllIds, "Expected remote-only ch_043 in reconciled series catalog")

        val row41 = chapterNodes.filterIsInstance<ListItemNode>().first { it.id == "pub_row_ch_041" }
        assertNotNull(row41.leadingAsset, "Expected generated cover AssetReference on chapter row")
        assertEquals("inspect:ch_041", row41.action)

        val chaptersSplit = chapterNodes.filterIsInstance<SplitPaneNode>().first { it.id == "pub_chapters_split" }
        assertTrue(chaptersSplit.resizable)

        // Drill into ch_040 Images workspace (Series -> Chapters -> Images)
        eventsFlow.emit(UiEvent.Click("pub_images_btn_ch_040", "open_images:ch_040"))
        advanceUntilIdle()

        val imageNodes = collectNodes(emittedTrees.last().root)
        val imageAllIds = imageNodes.map { it.id }
        assertEquals(imageAllIds.size, imageAllIds.toSet().size, "Images tree node IDs must be unique")
        val initialGrid = imageNodes.filterIsInstance<GridNode>().first { it.id == "insp_pages_grid" }
        assertTrue(initialGrid.reorderable, "Expected page preview grid to be reorderable")
        assertNull(initialGrid.columns, "Expected adaptive responsive columns on compact page grid")
        assertEquals(6, initialGrid.children.size, "Expected 6 sample pages in initial chapter grid")

        val previewThumb = imageNodes.filterIsInstance<ImageNode>().first { it.id == initialGrid.children[0].id.let { c -> "insp_page_thumb_" + c.removePrefix("insp_page_card_") } }
        assertEquals(96, previewThumb.modifier.heightDp, "Expected compact bounded tile thumbnail height")

        // Select second page preview, reorder grid pages, resize split pane, and exercise pickFiles/saveFile/openInteractiveTool
        val firstCardId = initialGrid.children[0].id
        val secondCardId = initialGrid.children[1].id
        val secondPageId = secondCardId.removePrefix("insp_page_card_")
        val reorderedCardIds = listOf(secondCardId, firstCardId) + initialGrid.children.drop(2).map { it.id }
        eventsFlow.emit(UiEvent.Click("insp_page_thumb_$secondPageId", "select_page:$secondPageId"))
        eventsFlow.emit(
            UiEvent.ItemsReordered(
                nodeId = "insp_pages_grid",
                itemIds = reorderedCardIds,
                fromIndex = 1,
                toIndex = 0
            )
        )
        eventsFlow.emit(UiEvent.ValueChanged("pub_images_split", 0.58))
        advanceUntilIdle()

        val afterReorderNodes = collectNodes(emittedTrees.last().root)
        val reorderedGrid = afterReorderNodes.filterIsInstance<GridNode>().first { it.id == "insp_pages_grid" }
        assertEquals(secondCardId, reorderedGrid.children.first().id, "Expected reordered page grid order to persist")
        val updatedSplit = afterReorderNodes.filterIsInstance<SplitPaneNode>().first { it.id == "pub_images_split" }
        assertEquals(0.58f, updatedSplit.splitFraction)

        eventsFlow.emit(UiEvent.Click("insp_add_pages_btn", "pick_page_files"))
        eventsFlow.emit(UiEvent.Click("insp_export_manifest_btn", "export_chapter_json"))
        eventsFlow.emit(UiEvent.Click("insp_open_board_btn", "open_page_in_board"))
        advanceUntilIdle()

        val afterPickNodes = collectNodes(emittedTrees.last().root)
        val afterPickGrid = afterPickNodes.filterIsInstance<GridNode>().first { it.id == "insp_pages_grid" }
        assertEquals(7, afterPickGrid.children.size, "Expected picked page appended to grid")
        assertEquals(1, sessionContext.savedFiles.size, "Expected saveFile called for chapter JSON export")

        // Assert workspace shortcuts & OS file drop target modifiers and trigger both events
        val scaffoldNode = emittedTrees.last().root as ScaffoldNode
        assertTrue(scaffoldNode.modifier.shortcuts.any { it.action == "save_draft_changes" && it.ctrl })
        assertTrue(scaffoldNode.modifier.shortcuts.any { it.action == "shortcut_escape" && it.key == UiShortcut.KEY_ESCAPE })

        val imgPane = afterPickNodes.first { it.id == "img_grid_pane" }
        assertNotNull(imgPane.modifier.dropTarget)
        assertEquals("drop_page_files", imgPane.modifier.dropTarget?.action)
        assertTrue(imgPane.modifier.shortcuts.any { it.key == UiShortcut.KEY_DELETE && it.action == "remove_selected_page" })

        eventsFlow.emit(
            UiEvent.FilesDropped(
                nodeId = "img_grid_pane",
                action = "drop_page_files",
                paths = listOf("/tmp/dropped_page_08.webp")
            )
        )
        advanceUntilIdle()
        val afterDropGrid = collectNodes(emittedTrees.last().root).filterIsInstance<GridNode>().first { it.id == "insp_pages_grid" }
        assertEquals(8, afterDropGrid.children.size, "Expected OS-dropped page image appended to grid")

        eventsFlow.emit(
            UiEvent.ShortcutTriggered(
                nodeId = "img_grid_pane",
                action = "remove_selected_page",
                key = UiShortcut.KEY_DELETE
            )
        )
        eventsFlow.emit(
            UiEvent.ShortcutTriggered(
                nodeId = "publisher_scaffold",
                action = "save_draft_changes",
                key = "S",
                ctrl = true
            )
        )
        advanceUntilIdle()
        val afterDeleteShortcutGrid = collectNodes(emittedTrees.last().root).filterIsInstance<GridNode>().first { it.id == "insp_pages_grid" }
        assertEquals(7, afterDeleteShortcutGrid.children.size, "Expected Delete shortcut to remove selected page")

        val handoff = sessionContext.openedTools.firstOrNull()
        assertNotNull(handoff)
        assertEquals("interactiveBoardCompositor", handoff.first)
        assertEquals("series_obsidian", handoff.second["seriesId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("ch_040", handoff.second["chapterId"]?.jsonPrimitive?.contentOrNull)
        val passedBgJson = handoff.second["backgroundAssetJson"]?.jsonPrimitive?.contentOrNull
        assertNotNull(passedBgJson, "Expected backgroundAssetJson passed to Board Compositor")
        assertTrue(Json.decodeFromString(AssetReference.serializer(), passedBgJson) is AssetReference)

        // Navigate back up (Images -> Chapters -> Series) using internal nav_back
        eventsFlow.emit(UiEvent.Click("pub_nav_back_btn", "nav_back"))
        advanceUntilIdle()
        assertTrue(collectNodes(emittedTrees.last().root).any { it.id == "pub_chapters_split" }, "Expected nav_back to return to Chapters")

        // Select ch_041 and trigger batch push capability
        eventsFlow.emit(UiEvent.ToggleChanged("pub_chk_ch_041", true))
        eventsFlow.emit(UiEvent.Click("pub_push_selected_btn", "push_selected"))
        advanceUntilIdle()

        assertTrue(
            sessionContext.capabilityJobs.value.any {
                it.capabilityName == "publishChapterBatch" && it.status == CapabilityJobStatus.COMPLETED
            },
            "publishChapterBatch capability job should have completed"
        )
        assertTrue(
            testContext.notifications.any { it.second.contains("publishChapterBatch") },
            "Expected persistent notification emitted after sync job completion"
        )
        val reconciledAfterPush = CompleteExampleCatalogStore.reconcile(
            CompleteExampleCatalogStore.loadLocalCatalog(testContext.storage),
            CompleteExampleCatalogStore.loadRemoteCatalog(testContext.storage)
        )
        assertEquals(
            ChapterSyncStatus.PUBLISHED,
            reconciledAfterPush.first { it.id == "ch_041" }.status
        )

        // Navigate back from Chapters to Series
        eventsFlow.emit(UiEvent.Click("pub_nav_back_btn", "nav_back"))
        advanceUntilIdle()
        assertTrue(collectNodes(emittedTrees.last().root).any { it.id == "pub_series_split" }, "Expected nav_back to return to Series")

        // Switch to queue tab and verify job card appears
        eventsFlow.emit(UiEvent.TabSelected("pub_tabs", "queue"))
        advanceUntilIdle()
        val queueNodeIds = collectNodes(emittedTrees.last().root).map { it.id }
        assertTrue(queueNodeIds.any { it.startsWith("queue_card_") }, "Expected capability job card in queue tab")

        // Switch to settings tab, mutate apiKey, save encrypted secret, pick repo folder, ping HTTP endpoint, & toggle feature lock
        eventsFlow.emit(UiEvent.TabSelected("pub_tabs", "settings"))
        advanceUntilIdle()
        val settingsNodes = collectNodes(emittedTrees.last().root)
        val secretInput = settingsNodes.filterIsInstance<TextInputNode>().first { it.id == "cfg_secret_token_input" }
        assertTrue(secretInput.isSecret, "Expected secret input field to mask input")

        eventsFlow.emit(UiEvent.TextChanged("cfg_api_key_input", "live_secret_token"))
        eventsFlow.emit(UiEvent.TextChanged("cfg_secret_token_input", "oauth_refresh_xyz"))
        eventsFlow.emit(UiEvent.Click("cfg_save_secret_btn", "save_secret_token"))
        eventsFlow.emit(UiEvent.Click("cfg_pick_repo_btn", "pick_repo_folder"))
        eventsFlow.emit(UiEvent.Click("cfg_ping_webhook_btn", "ping_webhook"))
        eventsFlow.emit(UiEvent.Click("cfg_toggle_lock_btn", "toggle_feature_lock"))
        advanceUntilIdle()

        assertEquals("live_secret_token", settingsState.value["apiKey"]?.jsonPrimitive?.content)
        assertEquals("oauth_refresh_xyz", testContext.secretStorage.values["publisher_api_secret"])
        assertEquals("/tmp/local_cubari_repo", testContext.storage.get("chapter_publisher_repo_path")?.jsonPrimitive?.contentOrNull)
        assertTrue(testContext.fakeNetworkClient.requests.any { it.method == HttpMethod.GET })
        assertEquals(true, locksState.value["feature_unlocked"])

        eventsFlow.emit(UiEvent.Click("cfg_reset_api_key_btn", "reset_api_key"))
        eventsFlow.emit(
            UiEvent.FilesDropped(
                nodeId = "cfg_integrations_card",
                action = "drop_repo_folder",
                paths = listOf("/tmp/dropped_cubari_repo")
            )
        )
        advanceUntilIdle()
        assertFalse(settingsState.value.containsKey("apiKey"), "Expected clearSetting to remove overridden apiKey")
        assertEquals("/tmp/dropped_cubari_repo", testContext.storage.get("chapter_publisher_repo_path")?.jsonPrimitive?.contentOrNull)
        } finally {
            job.cancel()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun testInteractiveBoardCompositorReadsInitialParameters() = runTest {
        val plugin = CompleteExamplePlugin(CompleteExampleSettings())
        val entry = CompleteExamplePluginPluginEntry(plugin)
        val processor = entry.getInteractiveProcessor()?.getOrNull()
        assertNotNull(processor)

        val expectedPageAsset = AssetReference.Relative("assets/ch_040_page_2.png")
        val eventsFlow = MutableSharedFlow<UiEvent>()
        val emitted = mutableListOf<UiNodeTree>()
        val testContext = TestPluginContext()
        val sessionContext = TestWiredSessionContext(
            capabilityName = "interactiveBoardCompositor",
            entry = entry,
            scope = CoroutineScope(coroutineContext),
            baseContext = testContext,
            settingsState = MutableStateFlow(emptyMap()),
            initialParameters = mapOf(
                "seriesId" to JsonPrimitive("series_obsidian"),
                "chapterId" to JsonPrimitive("ch_040"),
                "chapterTitle" to JsonPrimitive("Ch. 40: Echoes"),
                "pageLabel" to JsonPrimitive("Page 02"),
                "releaseNotes" to JsonPrimitive("SFX check"),
                "backgroundAssetJson" to JsonPrimitive(Json.encodeToString(AssetReference.serializer(), expectedPageAsset))
            )
        )

        val job = launch {
            processor.openUiSession(sessionContext, eventsFlow).collect { emitted.add(it) }
        }
        try {
            advanceUntilIdle()
            val nodes = collectNodes(emitted.last().root)
            val titleInput = nodes.filterIsInstance<TextInputNode>().first { it.id == "titleInput" }
            assertEquals("Ch. 40: Echoes — Page 02", titleInput.text)
            val board = nodes.filterIsInstance<BoardNode>().first { it.id == "drawingBoard" }
            assertEquals(expectedPageAsset, board.backgroundAsset)

            eventsFlow.emit(UiEvent.Click("backToPublisherBtn", "back_to_publisher"))
            advanceUntilIdle()
            val backHandoff = sessionContext.openedTools.firstOrNull()
            assertNotNull(backHandoff)
            assertEquals("interactiveChapterPublisher", backHandoff.first)
            assertEquals("IMAGES", backHandoff.second["navLevel"]?.jsonPrimitive?.contentOrNull)
            assertEquals("ch_040", backHandoff.second["chapterId"]?.jsonPrimitive?.contentOrNull)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testPublishChapterBatchPauseAndResume() = runTest {
        val plugin = CompleteExamplePlugin(CompleteExampleSettings())
        val signals = FakePluginSignalManager()
        val context = TestPluginContext(signals = signals)

        val signalJob = launch {
            delay(30)
            signals.sendSignal(PluginSignal.PAUSE)
        }

        val chapterIds = Json.encodeToString(listOf("ch_041", "ch_042"))
        val pausedResult = plugin.publishChapterBatch(
            chapterIdsJson = chapterIds,
            environment = "STAGING",
            simulatedStepDelayMs = 80L,
            resumeState = null,
            context = context
        )
        signalJob.join()
        assertTrue(pausedResult is ExecutionResult.Paused, "Expected Paused result but got $pausedResult")

        val resumedResult = plugin.publishChapterBatch(
            chapterIdsJson = chapterIds,
            environment = "STAGING",
            simulatedStepDelayMs = 5L,
            resumeState = pausedResult.resumeState,
            context = context
        )
        assertTrue(resumedResult is ExecutionResult.Success, "Expected Success on resume but got $resumedResult")
        val localAfter = CompleteExampleCatalogStore.loadLocalCatalog(context.storage)
        assertEquals(ChapterSyncStatus.PUBLISHED, localAfter.first { it.id == "ch_042" }.status)
    }

    @Test
    fun testInteractiveToolCapabilityManifestGeneration() {
        val manifest = CompleteExamplePluginManifest.manifest

        val compositorCap = manifest.capabilities.find { it.name == "interactiveBoardCompositor" }
        assertNotNull(compositorCap, "interactiveBoardCompositor capability must exist in manifest")
        assertTrue(compositorCap.isInteractive, "interactiveBoardCompositor must be flagged as interactive")
        assertEquals(CapabilityContext.INTERACTIVE_ONLY, compositorCap.context)

        val publisherCap = manifest.capabilities.find { it.name == "interactiveChapterPublisher" }
        assertNotNull(publisherCap, "interactiveChapterPublisher capability must exist in manifest")
        assertTrue(publisherCap.isInteractive, "interactiveChapterPublisher must be flagged as interactive")
        assertEquals(CapabilityContext.INTERACTIVE_ONLY, publisherCap.context)

        val pushCap = manifest.capabilities.find { it.name == "publishChapterBatch" }
        assertNotNull(pushCap)
        assertTrue(pushCap.isPausable, "publishChapterBatch must declare isPausable=true")

        val pullCap = manifest.capabilities.find { it.name == "pullRemoteChapters" }
        assertNotNull(pullCap)
        assertTrue(pullCap.isPausable, "pullRemoteChapters must declare isPausable=true")

        assertNotNull(manifest.capabilities.find { it.name == "renderCompositedPage" })
        assertNotNull(manifest.capabilities.find { it.name == "queryChapterCatalog" })
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun testChapterPublisherBatch1ControlsAndModifiers() = runTest {
        val plugin = CompleteExamplePlugin(CompleteExampleSettings(userId = "batch1_tester"))
        val entry = CompleteExamplePluginPluginEntry(plugin)
        val interactiveProcessor = entry.getInteractiveProcessor()?.getOrNull()
        assertNotNull(interactiveProcessor)

        val eventsFlow = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
        val emittedTrees = mutableListOf<UiNodeTree>()
        val fakeFs = FakePluginFileSystem()
        val settingsState = MutableStateFlow<Map<String, JsonElement>>(
            mapOf(
                "userId" to JsonPrimitive("batch1_tester"),
                "apiKey" to JsonPrimitive("")
            )
        )
        val testContext = TestPluginContext(
            fileSystem = fakeFs,
            initialSettings = settingsState.value
        ) { updated -> settingsState.value = updated }

        val locksState = MutableStateFlow(mapOf("feature_unlocked" to false))
        val sessionScope = CoroutineScope(coroutineContext)
        val sessionContext = TestWiredSessionContext(
            capabilityName = "interactiveChapterPublisher",
            entry = entry,
            scope = sessionScope,
            baseContext = testContext,
            settingsState = settingsState,
            locksState = locksState
        )

        val job = launch {
            interactiveProcessor.openUiSession(sessionContext, eventsFlow).collect { tree ->
                emittedTrees.add(tree)
            }
        }
        try {
            advanceUntilIdle()
            assertTrue(emittedTrees.isNotEmpty(), "Publisher session must emit initial UiNodeTree")

            val initialNodes = collectNodes(emittedTrees.last().root)
            val seriesSearch = initialNodes.filterIsInstance<SearchBarNode>()
                .first { it.id == "pub_series_search_input" }
            assertTrue(seriesSearch.suggestions.isNotEmpty())
            assertTrue(!seriesSearch.modifier.tooltip.isNullOrBlank())

            val seriesRow = initialNodes.filterIsInstance<ListItemNode>()
                .first { it.id.startsWith("series_row_") }
            assertEquals(ShapeRole.MEDIUM, seriesRow.modifier.cornerRadius)
            assertEquals(3, seriesRow.modifier.contextMenu.size)

            eventsFlow.emit(UiEvent.TabSelected("pub_tabs", "settings"))
            advanceUntilIdle()

            val settingsNodes = collectNodes(emittedTrees.last().root)
            val priorityRadio = settingsNodes.filterIsInstance<RadioGroupNode>()
                .first { it.id == "cfg_priority_radio" }
            assertEquals("BALANCED", priorityRadio.selectedKey)

            val batchInput = settingsNodes.filterIsInstance<NumberInputNode>()
                .first { it.id == "cfg_batch_size_input" }
            assertEquals(4.0, batchInput.value)

            eventsFlow.emit(UiEvent.OptionSelected("cfg_priority_radio", "FAST"))
            eventsFlow.emit(UiEvent.ValueChanged("cfg_batch_size_input", 12.0))
            eventsFlow.emit(UiEvent.ToggleChanged("cfg_manifest_accordion", true))
            advanceUntilIdle()

            val updatedNodes = collectNodes(emittedTrees.last().root)
            val accordion = updatedNodes.filterIsInstance<AccordionNode>()
                .first { it.id == "cfg_manifest_accordion" }
            assertTrue(accordion.expanded)

            val summaryText = updatedNodes.filterIsInstance<TextNode>()
                .first { it.id == "cfg_snapshot_summary" }
            assertTrue(summaryText.selectable)
            assertEquals(1, summaryText.maxLines)
            assertTrue(summaryText.overflowEllipsis)
            assertTrue(summaryText.text.contains("Priority: FAST"))
            assertTrue(summaryText.text.contains("Batch Size: 12"))

            val codeBlock = updatedNodes.filterIsInstance<CodeBlockNode>()
                .first { it.id == "cfg_manifest_code_block" }
            assertEquals("json", codeBlock.language)
            assertTrue(codeBlock.code.contains("\"batchSize\": 12"))
        } finally {
            job.cancel()
        }
    }
}




