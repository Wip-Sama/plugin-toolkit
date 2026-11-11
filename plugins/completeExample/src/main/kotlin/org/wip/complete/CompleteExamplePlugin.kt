package org.wip.complete

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.Flow
import org.wip.plugintoolkit.api.ErrorDetail
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.watchProcess
import org.wip.plugintoolkit.api.ConditionOperator
import org.wip.plugintoolkit.api.sdui.BoardGridStyle
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardShapeType
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.BoardViewportConfig
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.uiTree
import kotlinx.coroutines.flow.channelFlow
import org.wip.plugintoolkit.api.annotations.Capability
import org.wip.plugintoolkit.api.annotations.CapabilityContext
import org.wip.plugintoolkit.api.annotations.CapabilityInput
import org.wip.plugintoolkit.api.annotations.CapabilityOutput
import org.wip.plugintoolkit.api.annotations.CapabilityParam
import org.wip.plugintoolkit.api.annotations.CapabilityResult
import org.wip.plugintoolkit.api.annotations.InteractiveTool
import org.wip.plugintoolkit.api.annotations.DependsOn
import org.wip.plugintoolkit.api.annotations.ParameterGroup
import org.wip.plugintoolkit.api.annotations.PluginAction
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.annotations.PluginLoad
import org.wip.plugintoolkit.api.annotations.PluginSetting
import org.wip.plugintoolkit.api.annotations.PluginSetup
import org.wip.plugintoolkit.api.annotations.PluginUpdate
import org.wip.plugintoolkit.api.annotations.PluginValidate
import org.wip.plugintoolkit.api.annotations.PluginLocks
import kotlinx.serialization.json.booleanOrNull
import org.wip.plugintoolkit.api.annotations.RequiresLock
import org.wip.plugintoolkit.api.annotations.RequiresSetting
import org.wip.plugintoolkit.api.annotations.ResumeState
import org.wip.plugintoolkit.api.annotations.ComplexObject as ComplexObjectAnnotation
import java.io.File

enum class AIModelArchitecture {
    FAST_INFERENCE,
    HIGH_PRECISION,
    CUSTOM_CHECKPOINT
}

data class CompleteExampleSettings(
    @PluginSetting(
        description = "Public configuration value example",
        defaultValue = "default_api_key"
    ) val apiKey: String? = "default_api_key",

    @PluginSetting(
        description = "Secret token for secure operations",
        required = true,
        secret = true
    ) val secretToken: String? = "secret-12345",

    @PluginSetting(
        description = "Required user identifier",
        required = true
    ) val userId: String? = "user_demo",

    @PluginSetting(
        description = "Supported AI model architectures (multi-selection)",
        defaultValue = "[\"FAST_INFERENCE\", \"HIGH_PRECISION\"]"
    ) val enabledArchitectures: Set<AIModelArchitecture>? = setOf(AIModelArchitecture.FAST_INFERENCE)
)

@Serializable
data class ExampleProgressState(
    val currentStep: Int,
    val totalSteps: Int,
    val accumulatedValue: String
)

enum class RestrictedFeatureEnum {
    STANDARD,
    
    @RequiresLock(["feature_unlocked"])
    EXPERIMENTAL
}

@ComplexObjectAnnotation(
    id = "org.wip.complete.UserDataPacket",
    description = "A complex data packet containing user statistics and tags.",
    version = 1
)
@Serializable
data class UserDataPacket(
    val packetId: String,
    val score: Double,
    val tags: List<String>
)

@ComplexObjectAnnotation(
    id = "org.wip.complete.ColorPaletteResult",
    description = "Color palette processing result.",
    version = 1
)
@Serializable
data class ColorPaletteResult(
    @CapabilityResult(name = "primaryColor", description = "Primary RGB color", semanticTypes = ["color/rgb"]) val primaryColor: String,
    @CapabilityResult(name = "accentColor", description = "Accent RGB color", semanticTypes = ["color/rgb"]) val accentColor: String,
    @CapabilityResult(name = "packet", description = "Processed packet metadata") val packet: UserDataPacket
)

@Serializable
data class CustomDataResult(
    @CapabilityResult(name = "status", description = "Operation status code") val status: Int,
    @CapabilityResult(name = "message", description = "Summary text message") val message: String
)

@Serializable
data class PostProcessingOptions(
    @CapabilityParam(description = "Apply color grading filter", defaultValue = "false")
    val applyFilter: Boolean = false,

    @CapabilityParam(description = "Filter intensity level", defaultValue = "0.8", isAdvanced = true)
    @DependsOn(param = "applyFilter", value = "true")
    val filterIntensity: Double = 0.8
)

@Serializable
data class ModelInferenceOptions(
    @CapabilityParam(description = "Temperature for sampling", defaultValue = "0.7")
    val temperature: Double = 0.7,

    @CapabilityParam(description = "Maximum output tokens", defaultValue = "1024", isAdvanced = true)
    val maxTokens: Int = 1024,

    @ParameterGroup(prefix = "post_")
    val postProcessing: PostProcessingOptions = PostProcessingOptions()
)

enum class FeatureMode {
    @RequiresSetting(["apiKey"])
    ONLINE,

    @RequiresSetting(["secretToken"])
    SECURE,

    @RequiresLock(["feature_unlocked"])
    EXPERIMENTAL,
    LOCAL
}

@PluginInfo(
    id = "org.wip.complete",
    name = "Complete Example Plugin",
    version = "2.2.0",
    description = "Complete showcase of plugin API features including settings, validation, signals, storage, file system, lifecycle hooks, flow contexts, and server-driven interactive SDUI.",
    supportedOs = [OS.WINDOWS, OS.LINUX, OS.MACOS]
)
class CompleteExamplePlugin(val settings: CompleteExampleSettings) : InteractiveUiProcessor {

    @PluginLoad
    fun onLoad(logger: PluginLogger): Result<Unit> {
        logger.info("Executing PluginLoad lifecycle hook for CompleteExamplePlugin...")
        return Result.success(Unit)
    }

    @PluginSetup
    fun onSetup(logger: PluginLogger, fileSystem: PluginFileSystem): Result<Unit> {
        logger.info("Executing PluginSetup lifecycle hook for CompleteExamplePlugin...")
        return Result.success(Unit)
    }

    @PluginValidate
    fun validate(logger: PluginLogger, pluginContext: PluginContext): Result<Unit> {
        logger.info("Executing PluginValidate lifecycle hook for CompleteExamplePlugin settings...")
        if (settings.secretToken.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("secretToken must not be blank"))
        }
        return Result.success(Unit)
    }

    @PluginUpdate
    fun onUpdate(logger: PluginLogger, context: PluginContext): Result<Unit> {
        logger.info("Executing PluginUpdate lifecycle hook for CompleteExamplePlugin...")
        return Result.success(Unit)
    }

    @PluginAction(
        name = "Reset Plugin State",
        description = "Clears cached plugin data and resets counters.",
        showToast = true,
        toastMessage = "CompleteExample: State reset complete!"
    )
    suspend fun resetState(logger: PluginLogger) {
        logger.info("Resetting complete example plugin state...")
        delay(100)
        logger.info("State reset complete.")
    }

    @PluginAction(name = "Execute Health Diagnostic", description = "Runs diagnostic tasks reporting rich progress and secondary progress.")
    suspend fun runDiagnostic(logger: PluginLogger, progress: ProgressReporter) {
        logger.info("Starting complete example health diagnostic...")
        progress.report(ProgressData.percentage(0.2f, "Checking system health - 120 items/s"))
        progress.reportSecondary(ProgressData.indeterminate("Validating secondary subsystems"))
        delay(50)
        progress.report(ProgressData.ratio(12.3, 14.5, "MB", "Verifying cache integrity"))
        progress.reportSecondary(ProgressData.percentage(1.0f, "Subsystems OK"))
        delay(50)
        progress.clearSecondary()
        progress.report(1.0f, "Diagnostic completed successfully")
        logger.info("Health diagnostic complete.")
    }

    @Capability(
        name = "capabilityWithNetworkAndProgress",
        description = "Showcase of network client usage, throughput tracking, primary/secondary progress reporting, and retry backoff."
    )
    suspend fun capabilityWithNetworkAndProgress(
        @CapabilityParam(description = "Total data to process in MB", defaultValue = "10.0") dataMb: Double,
        context: PluginContext,
        networkClient: PluginNetworkClient
    ): ExecutionResult {
        context.logger.info("Starting capabilityWithNetworkAndProgress for $dataMb MB...")
        context.progress.report(ProgressData.ratio(0.0, dataMb, "MB", "Starting data transfer"))

        // Simulate network throughput and secondary progress for rate limit / backoff
        context.progress.reportSecondary(ProgressData.percentage(0.5f, "Simulating rate limit backoff: 500ms remaining"))
        delay(50)
        context.progress.clearSecondary()

        val bytesToRecord = (dataMb * 1024 * 1024).toLong()
        context.recordNetworkUsage(bytesRead = bytesToRecord, bytesWritten = 1024L, throughputBytesPerSec = 5_000_000L)
        context.progress.report(ProgressData.ratio(dataMb, dataMb, "MB", "Transfer complete"))
        return ExecutionResult.Success(PluginResponse(result = JsonPrimitive("Processed $dataMb MB successfully")))
    }

    @Capability(
        name = "capabilityWithPauseResume",
        description = "Showcase of a long-running, pausable capability with state persistence and cancellation checks.",
        supportsPause = true,
        maxConcurrentExecutions = 2
    )
    suspend fun capabilityWithPauseResume(
        @CapabilityParam(description = "Number of iteration steps", defaultValue = "5") totalSteps: Int,
        @CapabilityParam(description = "Delay per step in milliseconds", defaultValue = "200") stepDelayMs: Long,
        @ResumeState resumeState: JsonElement?,
        context: PluginContext
    ): ExecutionResult {
        val state = resumeState?.let {
            try {
                Json.decodeFromJsonElement(ExampleProgressState.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }

        var currentStep = state?.currentStep ?: 0
        var accumulatedValue = state?.accumulatedValue ?: ""
        var isPaused = false

        context.onSignal { signal ->
            if (signal == PluginSignal.PAUSE) {
                isPaused = true
            }
        }

        while (currentStep < totalSteps) {
            if (isPaused) {
                context.logger.info("Capability paused at step $currentStep")
                val pausedState = ExampleProgressState(currentStep, totalSteps, accumulatedValue)
                return ExecutionResult.Paused(Json.encodeToJsonElement(ExampleProgressState.serializer(), pausedState))
            }
            accumulatedValue += " step_$currentStep"
            currentStep++
            context.progress.report(currentStep.toFloat() / totalSteps)
            delay(stepDelayMs)
        }

        return ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(accumulatedValue.trim())))
    }

    @Capability(
        name = "capabilityWithFileAccess",
        description = "Showcase of file system access reading from input and writing to output paths."
    )
    fun capabilityWithFileAccess(
        @CapabilityInput(description = "Source text file path", semanticTypes = ["file/text"]) inputPath: String,
        @CapabilityOutput(
            description = "Destination report file path",
            autogeneratedPattern = "{inputPath}/../../output_report.txt",
            isDestructive = true,
            semanticTypes = ["file/text"]
        ) outputPath: String
    ): CustomDataResult {
        val inputFile = File(inputPath)
        val content = if (inputFile.exists()) inputFile.readText() else "No content"
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("Processed Content:\n" + content.uppercase())
        return CustomDataResult(200, "Successfully processed $inputPath -> $outputPath")
    }

    @Capability(
        name = "capabilityWithFileDefaults",
        description = "Showcase of handling file inputs and outputs with default values and autogenerated patterns."
    )
    fun capabilityWithFileDefaults(
        @CapabilityInput(
            description = "Input configuration file path",
            defaultValue = "config/default_input.txt",
            semanticTypes = ["file/text"]
        ) inputPath: String? = "config/default_input.txt",
        @CapabilityOutput(
            description = "Output log file path",
            autogeneratedPattern = "{inputPath}/../../output_default.txt",
            defaultValue = "output/default_output.txt",
            isDestructive = false,
            semanticTypes = ["file/text"]
        ) outputPath: String? = "output/default_output.txt"
    ): String {
        val effectiveInput = inputPath ?: "config/default_input.txt"
        val effectiveOutput = outputPath ?: "output/default_output.txt"
        return "Processed file defaults: input='$effectiveInput', output='$effectiveOutput'"
    }

    @Capability(
        name = "capabilityWithDataStorage",
        description = "Showcase of key-value persistence using PluginStorage."
    )
    suspend fun capabilityWithDataStorage(
        @CapabilityParam(description = "Storage key name") key: String,
        @CapabilityParam(description = "Value to store") value: String,
        context: PluginContext
    ): String {
        context.storage.put(key, JsonPrimitive(value))
        val retrieved = context.storage.get(key)?.jsonPrimitive?.content ?: "null"
        return "Stored and retrieved key '$key': $retrieved"
    }

    @Capability(
        name = "capabilityWithFlowContext",
        description = "Showcase of capability context scoped exclusively for visual flows.",
        context = CapabilityContext.FLOW_ONLY,
        requiresSettings = ["userId"]
    )
    fun capabilityWithFlowContext(
        @CapabilityParam(description = "Execution mode selection") mode: FeatureMode,
        @CapabilityParam(description = "Allowed model architectures (multi-select collection)") architectures: Collection<AIModelArchitecture> = listOf(AIModelArchitecture.FAST_INFERENCE),
        @CapabilityParam(description = "Configuration data map") config: Map<String, String> = emptyMap()
    ): String {
        return "Flow execution mode: ${mode.name}, architectures: ${architectures.joinToString { it.name }}, userId: ${settings.userId}, config entries: ${config.size}"
    }

    @Capability(
        name = "capabilityWithComplexObjectsAndSemanticTypes",
        description = "Showcase of processing complex objects and semantic types."
    )
    @CapabilityResult(
        name = "paletteResult",
        description = "Generated color palette with updated user packet",
        semanticTypes = ["color/rgb", "application/json"]
    )
    fun capabilityWithComplexObjectsAndSemanticTypes(
        @CapabilityParam(description = "Input user data packet") packet: UserDataPacket,
        @CapabilityParam(
            description = "Base RGB color string",
            defaultValue = "rgb(120, 50, 200)",
            semanticTypes = ["color/rgb"]
        ) baseColor: String
    ): ColorPaletteResult {
        return ColorPaletteResult(
            primaryColor = baseColor,
            accentColor = "rgb(255, 255, 255)",
            packet = packet.copy(score = packet.score + 10.0)
        )
    }

    @PluginLocks
    suspend fun checkLocks(context: PluginContext): Map<String, Boolean> {
        val isUnlocked = context.storage.get("feature_unlocked")?.let {
            (it as? JsonPrimitive)?.booleanOrNull
        } ?: false
        return mapOf("feature_unlocked" to isUnlocked)
    }

    @PluginAction(
        name = "Toggle Feature Lock",
        description = "Writes a boolean flag to storage to lock or unlock restricted plugin features.",
        showToast = true
    )
    suspend fun toggleFeatureLock(
        @CapabilityParam(description = "True to unlock features, false to lock") unlocked: Boolean,
        context: PluginContext
    ) {
        context.logger.info("Setting feature lock state in storage: unlocked=$unlocked")
        context.storage.put("feature_unlocked", JsonPrimitive(unlocked))
        context.showToast("Feature lock state changed: unlocked=$unlocked")
    }

    @PluginAction(
        name = "Update Api Key Setting",
        description = "Showcase updating plugin settings dynamically via PluginContext."
    )
    suspend fun updateApiKeySetting(
        @CapabilityParam(description = "New API key value") newApiKey: String,
        context: PluginContext
    ) {
        context.logger.info("Updating apiKey setting dynamically: $newApiKey")
        context.updateSetting("apiKey", newApiKey)
    }

    @Capability(
        name = "capabilityWithLockRequirement",
        description = "Showcase of a capability restricted by a dynamic lock flag stored in plugin storage."
    )
    @RequiresLock(["feature_unlocked"])
    suspend fun capabilityWithLockRequirement(
        @CapabilityParam(description = "Select a feature to run") feature: RestrictedFeatureEnum?,
        context: PluginContext
    ): String {
        val storageValue = context.storage.get("feature_unlocked")
        val isUnlocked = (storageValue as? JsonPrimitive)?.booleanOrNull
        return "Feature is unlocked and operational! storageValue=$storageValue, isUnlocked=$isUnlocked, feature=$feature"
    }

    @Capability(
        name = "capabilityWithErrorDetail",
        description = "Showcase of structured, serializable error reporting using ErrorDetail."
    )
    fun capabilityWithErrorDetail(
        @CapabilityParam(description = "Whether to trigger an error", defaultValue = "true") triggerError: Boolean
    ): ExecutionResult {
        return if (triggerError) {
            ExecutionResult.Error(
                error = ErrorDetail(
                    message = "Simulated domain error with structured diagnostic details",
                    exceptionType = "DomainValidationException",
                    details = mapOf("errorCode" to "ERR_DEMO_001", "suggestion" to "Check configuration parameters")
                )
            )
        } else {
            ExecutionResult.Success(PluginResponse(result = JsonPrimitive("Operation completed successfully")))
        }
    }

    @Capability(
        name = "capabilityWithProcessWatcher",
        description = "Showcase of external process monitoring using PluginContext.watchProcess to track memory."
    )
    fun capabilityWithProcessWatcher(
        @CapabilityParam(description = "External message or argument to simulate CLI work", defaultValue = "echo-test") message: String,
        context: PluginContext
    ): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val process = ProcessBuilder(
            if (isWindows) listOf("cmd.exe", "/c", "echo", message)
            else listOf("echo", message)
        ).start()

        val watcher = context.watchProcess(process)
        val initialAlive = watcher.isAlive
        val mem = watcher.getCurrentMemoryBytes()
        val peak = watcher.getPeakMemoryBytes()
        process.waitFor()
        watcher.close()
        return "Watched process PID ${watcher.pid}: initialAlive=$initialAlive, mem=${mem}B, peak=${peak}B"
    }

    @Capability(
        name = "capabilityWithDynamicAndAdvancedParams",
        description = "Showcase of dynamic parameters that depend on other parameters/settings, along with advanced parameters."
    )
    fun capabilityWithDynamicAndAdvancedParams(
        @CapabilityParam(
            description = "Selected model architecture",
            defaultValue = "FAST_INFERENCE"
        ) modelArchitecture: AIModelArchitecture = AIModelArchitecture.FAST_INFERENCE,

        @CapabilityParam(
            description = "Custom model weights checkpoint file path",
            defaultValue = "models/custom.bin"
        )
        @DependsOn(param = "modelArchitecture", value = "CUSTOM_CHECKPOINT")
        customWeightsPath: String? = null,

        @CapabilityParam(
            description = "Inference batch size",
            defaultValue = "16",
            isAdvanced = true
        )
        batchSize: Int = 16,

        @CapabilityParam(
            description = "Quantization bits for precision tuning",
            defaultValue = "8",
            isAdvanced = true
        )
        @DependsOn(param = "modelArchitecture", operator = ConditionOperator.NOT_EQUALS, value = "FAST_INFERENCE")
        quantizationBits: Int? = 8,

        @CapabilityParam(
            description = "API Endpoint override when using remote API key setting",
            isAdvanced = true
        )
        @DependsOn(setting = "apiKey", operator = ConditionOperator.IS_NOT_BLANK)
        remoteEndpoint: String? = null,

        @CapabilityOutput(
            description = "Debug telemetry logs output",
            isAdvanced = true,
            semanticTypes = ["file/text"]
        )
        debugLogPath: String? = null
    ): String {
        return "Model: $modelArchitecture, customWeights: $customWeightsPath, batchSize: $batchSize, " +
                "quantization: $quantizationBits, remoteEndpoint: $remoteEndpoint, debugLog: $debugLogPath"
    }

    @Capability(
        name = "capabilityWithParameterGroup",
        description = "Showcase of grouped and nested parameters using @ParameterGroup unpacked into the manifest."
    )
    fun capabilityWithParameterGroup(
        @CapabilityParam(
            description = "Selected inference engine",
            defaultValue = "LOCAL_LLM"
        )
        engine: String = "LOCAL_LLM",

        @ParameterGroup(prefix = "llm_")
        @DependsOn(param = "engine", value = "LOCAL_LLM")
        inferenceOptions: ModelInferenceOptions = ModelInferenceOptions()
    ): String {
        return "Engine: $engine, Temp: ${inferenceOptions.temperature}, MaxTokens: ${inferenceOptions.maxTokens}, " +
                "Filter: ${inferenceOptions.postProcessing.applyFilter}, Intensity: ${inferenceOptions.postProcessing.filterIntensity}"
    }

    @InteractiveTool(
        name = "interactiveDrawingAndControls",
        description = "Interactive SDUI canvas showcasing reactive live controls, form synchronization, and vector drawing."
    )
    fun interactiveDrawingAndControls(): String {
        return "Interactive SDUI session active"
    }

    override fun openUiSession(
        context: UiSessionContext,
        events: Flow<UiEvent>
    ): Flow<UiNodeTree> = channelFlow {
        var canvasTitle = "Interactive Spatial Board"
        var brushWidth = 4.0
        var snapToGrid = false
        var brushColor = SemanticColor.PRIMARY
        var activeToolType = "SELECT"
        var counter = 0
        var clearRevision = 0
        var selectedLayerIds = setOf<String>()
        val layers = mutableListOf<BoardLayer>(
            BoardLayer.ShapeLayer(
                id = "callout_1",
                shapeType = BoardShapeType.CALLOUT_BUBBLE,
                position = NormalizedPoint(0.08f, 0.12f),
                size = NormalizedSize(0.28f, 0.18f),
                strokeColor = SemanticColor.PRIMARY,
                fillColor = SemanticColor.SURFACE,
                text = "Double-click text to edit!",
                zIndex = 1
            ),
            BoardLayer.TextLayer(
                id = "text_1",
                text = "Editable Overlay Caption",
                position = NormalizedPoint(0.12f, 0.36f),
                color = SemanticColor.ON_SURFACE,
                backgroundColor = SemanticColor.SURFACE,
                borderColor = SemanticColor.OUTLINE,
                role = TypographyRole.TITLE_MEDIUM,
                zIndex = 2
            )
        )

        fun renderTree(): UiNodeTree = uiTree {
            val activeBoardTool: BoardTool = when (activeToolType) {
                "SELECT" -> BoardTool.Select()
                "PAN" -> BoardTool.Pan
                "TEXT" -> BoardTool.PlaceText(defaultText = "New Caption", color = brushColor)
                "SHAPE" -> BoardTool.Shape(
                    shapeType = BoardShapeType.CALLOUT_BUBBLE,
                    strokeColor = brushColor,
                    fillColor = SemanticColor.SURFACE,
                    strokeWidth = brushWidth.toFloat()
                )
                "HIGHLIGHTER" -> BoardTool.Highlighter(color = brushColor, strokeWidth = (brushWidth * 2.5).toFloat())
                "ERASER" -> BoardTool.Eraser(radius = (brushWidth * 2).toFloat())
                else -> BoardTool.Brush(color = brushColor, strokeWidth = brushWidth.toFloat())
            }

            scaffold(id = "interactiveScaffold", modifier = UiModifier.fillMaxSize()) {
                content {
                    board(
                        id = "drawingBoard",
                        activeTool = activeBoardTool,
                        viewportConfig = BoardViewportConfig(
                            gridStyle = BoardGridStyle.DOTS,
                            snapToGrid = snapToGrid,
                            gridStep = 0.05f
                        ),
                        selectedLayerIds = selectedLayerIds,
                        clearRevision = clearRevision,
                        showToolbar = true,
                        showZoomControls = true,
                        modifier = UiModifier.fillMaxSize()
                    ) {
                        layers.forEach { layer(it) }
                        componentLayer(
                            id = "inspector_widget",
                            position = NormalizedPoint(0.08f, 0.62f),
                            size = NormalizedSize(0.32f, 0.24f),
                            zIndex = 3
                        ) {
                            column(spacing = SpacingSize.EXTRA_SMALL) {
                                text(
                                    text = canvasTitle,
                                    role = TypographyRole.TITLE_MEDIUM,
                                    color = SemanticColor.PRIMARY
                                )
                                text(
                                    text = "Selected: ${selectedLayerIds.joinToString().ifEmpty { "None" }}",
                                    role = TypographyRole.BODY_SMALL
                                )
                                button(
                                    id = "boardWidgetBtn",
                                    label = "Widget Taps: $counter",
                                    action = "increment_counter"
                                )
                            }
                        }
                    }
                }

                floatingPanel(
                    id = "controlsPanel",
                    anchor = FloatingAnchor.TOP_END,
                    title = "Board Controls",
                    isCollapsible = true,
                    isCollapsed = false
                ) {
                    textInput(
                        id = "titleInput",
                        text = canvasTitle,
                        label = "Board Title",
                        placeholder = "Enter title..."
                    )
                    dropdown(
                        id = "toolDropdown",
                        label = "Active Tool",
                        options = mapOf(
                            "SELECT" to "Select / Move / Edit",
                            "PAN" to "Pan Camera",
                            "TEXT" to "Place Text",
                            "SHAPE" to "Draw Callout Bubble",
                            "PEN" to "Freehand Pen",
                            "HIGHLIGHTER" to "Highlighter",
                            "ERASER" to "Eraser"
                        ),
                        selectedKey = activeToolType
                    )
                    dropdown(
                        id = "colorDropdown",
                        label = "Active Color",
                        options = mapOf(
                            "PRIMARY" to "Primary Blue",
                            "SECONDARY" to "Secondary Gray",
                            "ACCENT" to "Accent Teal",
                            "HIGHLIGHT" to "Warning Highlight",
                            "ERROR" to "Error Red"
                        ),
                        selectedKey = brushColor.name
                    )
                    toggle(
                        id = "snapToggle",
                        label = "Snap to Grid",
                        checked = snapToGrid
                    )
                    slider(
                        id = "widthSlider",
                        value = brushWidth,
                        min = 1.0,
                        max = 20.0,
                        steps = 19,
                        label = "Stroke Width: ${brushWidth.toInt()}px"
                    )
                    row(spacing = SpacingSize.SMALL) {
                        button(
                            id = "clearBtn",
                            label = "Clear Board",
                            action = "clear_canvas",
                            isPrimary = false,
                            isDestructive = true
                        )
                        button(
                            id = "counterBtn",
                            label = "Taps: $counter",
                            action = "increment_counter"
                        )
                    }
                    text(
                        id = "footerStats",
                        text = "Tool: $activeToolType | Color: ${brushColor.name} | Layers: ${layers.size + 1}",
                        role = TypographyRole.LABEL,
                        color = SemanticColor.ON_SURFACE
                    )
                }
            }
        }

        send(renderTree())

        events.collect { event ->
            when (event) {
                is UiEvent.Click -> {
                    when (event.action ?: event.nodeId) {
                        "clear_canvas", "clearBtn" -> {
                            layers.clear()
                            selectedLayerIds = emptySet()
                            clearRevision++
                            send(renderTree())
                        }
                        "increment_counter", "counterBtn", "boardWidgetBtn" -> {
                            counter++
                            send(renderTree())
                        }
                    }
                }
                is UiEvent.TextChanged -> {
                    if (event.nodeId == "titleInput") {
                        canvasTitle = event.text
                        send(renderTree())
                    }
                }
                is UiEvent.ValueChanged -> {
                    if (event.nodeId == "widthSlider") {
                        brushWidth = event.value
                        send(renderTree())
                    }
                }
                is UiEvent.ToggleChanged -> {
                    if (event.nodeId == "snapToggle") {
                        snapToGrid = event.checked
                        send(renderTree())
                    }
                }
                is UiEvent.OptionSelected -> {
                    if (event.nodeId == "toolDropdown") {
                        activeToolType = event.selectedKey
                        send(renderTree())
                    } else if (event.nodeId == "colorDropdown") {
                        brushColor = try {
                            SemanticColor.valueOf(event.selectedKey)
                        } catch (e: Exception) {
                            SemanticColor.PRIMARY
                        }
                        send(renderTree())
                    }
                }
                is UiEvent.PathCommitted -> {
                    if (event.nodeId == "drawingBoard") {
                        layers.add(
                            BoardLayer.PathLayer(
                                id = "path_${layers.size + 1}",
                                path = event.path,
                                zIndex = layers.size + 1
                            )
                        )
                        send(renderTree())
                    }
                }
                is UiEvent.BoardLayerSelected -> {
                    selectedLayerIds = event.selectedLayerIds.toSet()
                    send(renderTree())
                }
                is UiEvent.BoardLayerMoved -> {
                    val idx = layers.indexOfFirst { it.id == event.layerId }
                    if (idx >= 0) {
                        val updated = when (val existing = layers[idx]) {
                            is BoardLayer.TextLayer -> existing.copy(position = event.position)
                            is BoardLayer.ImageLayer -> existing.copy(position = event.position)
                            is BoardLayer.ShapeLayer -> existing.copy(position = event.position)
                            is BoardLayer.ComponentLayer -> existing.copy(position = event.position)
                            is BoardLayer.PathLayer -> existing
                        }
                        layers[idx] = updated
                        send(renderTree())
                    }
                }
                is UiEvent.BoardLayerResized -> {
                    val idx = layers.indexOfFirst { it.id == event.layerId }
                    if (idx >= 0) {
                        val updated = when (val existing = layers[idx]) {
                            is BoardLayer.TextLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ImageLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ShapeLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.ComponentLayer -> existing.copy(position = event.position, size = event.size)
                            is BoardLayer.PathLayer -> existing
                        }
                        layers[idx] = updated
                        send(renderTree())
                    }
                }
                is UiEvent.BoardTextEdited -> {
                    val idx = layers.indexOfFirst { it.id == event.layerId }
                    if (idx >= 0) {
                        val existing = layers[idx]
                        if (existing is BoardLayer.TextLayer) {
                            layers[idx] = existing.copy(text = event.text)
                            send(renderTree())
                        }
                    }
                }
                is UiEvent.BoardTextPlaced -> {
                    val newId = "text_${layers.size + 1}"
                    layers.add(
                        BoardLayer.TextLayer(
                            id = newId,
                            text = event.text,
                            position = event.position,
                            color = brushColor,
                            backgroundColor = SemanticColor.SURFACE,
                            borderColor = brushColor,
                            zIndex = layers.size + 1
                        )
                    )
                    selectedLayerIds = setOf(newId)
                    activeToolType = "SELECT"
                    send(renderTree())
                }
                is UiEvent.BoardShapeCommitted -> {
                    val newId = "shape_${layers.size + 1}"
                    layers.add(
                        BoardLayer.ShapeLayer(
                            id = newId,
                            shapeType = event.shapeType,
                            position = event.position,
                            size = event.size,
                            strokeColor = event.strokeColor,
                            fillColor = event.fillColor,
                            strokeWidth = event.strokeWidth,
                            zIndex = layers.size + 1
                        )
                    )
                    selectedLayerIds = setOf(newId)
                    activeToolType = "SELECT"
                    send(renderTree())
                }
                is UiEvent.BoardLayerDeleted -> {
                    val toDelete = event.layerIds.toSet()
                    layers.removeAll { it.id in toDelete }
                    selectedLayerIds = selectedLayerIds - toDelete
                    send(renderTree())
                }
                is UiEvent.BoardCanvasClicked -> {
                    selectedLayerIds = emptySet()
                    send(renderTree())
                }
                is UiEvent.Custom -> {
                    if (event.action == "SELECT_TOOL") {
                        activeToolType = when (event.payload?.jsonPrimitive?.content) {
                            "select" -> "SELECT"
                            "pan" -> "PAN"
                            "text" -> "TEXT"
                            "shape" -> "SHAPE"
                            "brush" -> "PEN"
                            "highlighter" -> "HIGHLIGHTER"
                            "eraser" -> "ERASER"
                            else -> activeToolType
                        }
                        send(renderTree())
                    } else if (event.action == "CLEAR") {
                        layers.clear()
                        selectedLayerIds = emptySet()
                        clearRevision++
                        send(renderTree())
                    }
                }
            }
        }
    }
}
