package org.wip.complete

import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.VectorPathData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    private class TestPluginContext(
        override val logger: PluginLogger = FakePluginLogger(),
        override val progress: ProgressReporter = FakeProgressReporter(),
        override val signals: PluginSignalManager = FakePluginSignalManager(),
        override val storage: PluginStorage = FakePluginStorage(),
        override val fileSystem: PluginFileSystem = FakePluginFileSystem(),
        initialSettings: Map<String, JsonElement> = emptyMap(),
        private val onSettingsMutated: (Map<String, JsonElement>) -> Unit = {}
    ) : PluginContext {
        val updatedSettings = mutableMapOf<String, JsonElement>().apply { putAll(initialSettings) }
        override val settings: Map<String, JsonElement> get() = updatedSettings

        override val cacheFileSystem: PluginFileSystem get() = fileSystem
        override val executionFileSystem: ExecutionFileSystem get() = throw UnsupportedOperationException()
        override val hostFileSystem: HostFileSystem get() = throw UnsupportedOperationException()

        override fun setRequiredAction(actionName: String?) {}

        override suspend fun updateSetting(key: String, value: JsonElement) {
            updatedSettings[key] = value
            onSettingsMutated(updatedSettings.toMap())
        }
    }

    private class TestWiredSessionContext(
        override val capabilityName: String,
        private val entry: PluginEntry,
        private val scope: CoroutineScope,
        private val baseContext: TestPluginContext,
        private val settingsState: MutableStateFlow<Map<String, JsonElement>>,
        private val locksState: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())
    ) : UiSessionContext, PluginContext by baseContext {
        private val jobsState = MutableStateFlow<List<CapabilityJobSnapshot>>(emptyList())
        private var jobCounter = 0

        override val sessionId: String = "test_session_$capabilityName"
        override val settingsFlow: StateFlow<Map<String, JsonElement>> = settingsState.asStateFlow()
        override val locksFlow: StateFlow<Map<String, Boolean>> = locksState.asStateFlow()
        override val capabilityJobs: StateFlow<List<CapabilityJobSnapshot>> = jobsState.asStateFlow()

        override fun completeSession(response: PluginResponse) {}
        override fun cancelSession(reason: String) {}

        override suspend fun updateSetting(key: String, value: JsonElement) {
            baseContext.updateSetting(key, value)
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
            is org.wip.plugintoolkit.api.sdui.SidebarNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is org.wip.plugintoolkit.api.sdui.FloatingPanelNode -> node.children.forEach { result.addAll(collectNodes(it)) }
            is DialogNode -> node.children.forEach { result.addAll(collectNodes(it)) }
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
        assertEquals(1, scaffold.floatingPanels.size)
        assertEquals("controlsPanel", scaffold.floatingPanels.first().id)
        assertEquals(FloatingAnchor.TOP_END, scaffold.floatingPanels.first().anchor)
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
        val sessionScope = kotlinx.coroutines.CoroutineScope(coroutineContext)
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

        assertTrue(emittedTrees.isNotEmpty(), "Expected initial publisher tree")
        val initialTree = emittedTrees.last()
        val roundTripped = Json.decodeFromString(
            UiNodeTree.serializer(),
            Json.encodeToString(UiNodeTree.serializer(), initialTree)
        )
        assertTrue(roundTripped.root is ScaffoldNode)

        val initialAllIds = collectNodes(initialTree.root).map { it.id }
        assertEquals(initialAllIds.size, initialAllIds.toSet().size, "Publisher tree node IDs must be unique")
        assertTrue("pub_row_ch_041" in initialAllIds, "Expected ch_041 in loaded catalog")
        assertTrue("pub_row_ch_043" in initialAllIds, "Expected remote-only ch_043 in reconciled catalog")

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
        val reconciledAfterPush = CompleteExampleCatalogStore.reconcile(
            CompleteExampleCatalogStore.loadLocalCatalog(testContext.storage),
            CompleteExampleCatalogStore.loadRemoteCatalog(testContext.storage)
        )
        assertEquals(
            ChapterSyncStatus.PUBLISHED,
            reconciledAfterPush.first { it.id == "ch_041" }.status
        )

        // Switch to queue tab and verify job card appears
        eventsFlow.emit(UiEvent.TabSelected("pub_tabs", "queue"))
        advanceUntilIdle()
        val queueNodeIds = collectNodes(emittedTrees.last().root).map { it.id }
        assertTrue(queueNodeIds.any { it.startsWith("queue_card_") }, "Expected capability job card in queue tab")

        // Switch to settings tab, mutate apiKey & toggle feature lock action
        eventsFlow.emit(UiEvent.TabSelected("pub_tabs", "settings"))
        advanceUntilIdle()
        eventsFlow.emit(UiEvent.TextChanged("cfg_api_key_input", "live_secret_token"))
        eventsFlow.emit(UiEvent.Click("cfg_toggle_lock_btn", "toggle_feature_lock"))
        advanceUntilIdle()

        assertEquals("live_secret_token", settingsState.value["apiKey"]?.jsonPrimitive?.content)
        assertEquals(true, locksState.value["feature_unlocked"])
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
}




