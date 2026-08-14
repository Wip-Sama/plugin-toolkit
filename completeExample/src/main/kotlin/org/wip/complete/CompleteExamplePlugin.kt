package org.wip.complete

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.annotations.Capability
import org.wip.plugintoolkit.api.annotations.CapabilityContext
import org.wip.plugintoolkit.api.annotations.CapabilityInput
import org.wip.plugintoolkit.api.annotations.CapabilityOutput
import org.wip.plugintoolkit.api.annotations.CapabilityParam
import org.wip.plugintoolkit.api.annotations.CapabilityResult
import org.wip.plugintoolkit.api.annotations.PluginAction
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.annotations.PluginUiPage
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

data class CompleteExampleSettings(
    @PluginSetting(
        description = "Public configuration value example",
        defaultValue = "default_api_key",
        minLength = 8,
        semanticTypes = ["text/plain"]
    ) val apiKey: String? = "default_api_key",

    @PluginSetting(
        description = "Secret token for secure operations",
        required = true,
        secret = true
    ) val secretToken: String? = "secret-12345",

    @PluginSetting(
        description = "Required user identifier",
        required = true
    ) val userId: String? = "user_demo"
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
    version = "1.0.0",
    description = "Complete showcase of plugin API features including settings, validation, signals, storage, file system, lifecycle hooks, and flow contexts.",
    supportedOs = [OS.WINDOWS, OS.LINUX, OS.MACOS]
)
@PluginUiPage(
    id = "essentials",
    title = "Essential capabilities",
    description = "Common storage and file operations.",
    capabilityNames = ["capabilityWithFileAccess", "capabilityWithDataStorage"]
)
@PluginUiPage(
    id = "advanced",
    title = "Advanced capabilities",
    capabilityNames = ["capabilityWithPauseResume", "capabilityWithComplexObjectsAndSemanticTypes"]
)
class CompleteExamplePlugin(val settings: CompleteExampleSettings) {

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

    @PluginAction(name = "Reset Plugin State", description = "Clears cached plugin data and resets counters.")
    suspend fun resetState(logger: PluginLogger) {
        logger.info("Resetting complete example plugin state...")
        delay(100)
        logger.info("State reset complete.")
    }

    @PluginAction(name = "Execute Health Diagnostic", description = "Runs diagnostic tasks reporting progress.")
    suspend fun runDiagnostic(logger: PluginLogger, progress: ProgressReporter) {
        logger.info("Starting complete example health diagnostic...")
        for (i in 1..5) {
            progress.report(i / 5f)
            delay(100)
        }
        logger.info("Health diagnostic complete.")
    }

    @Capability(
        name = "capabilityWithPauseResume",
        description = "Showcase of a long-running, pausable capability with state persistence and cancellation checks.",
        supportsPause = true
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
        @CapabilityParam(description = "Configuration data map") config: Map<String, String>
    ): String {
        return "Flow execution mode: ${mode.name}, userId: ${settings.userId}, config entries: ${config.size}"
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
        description = "Writes a boolean flag to storage to lock or unlock restricted plugin features."
    )
    suspend fun toggleFeatureLock(
        @CapabilityParam(description = "True to unlock features, false to lock") unlocked: Boolean,
        context: PluginContext
    ) {
        context.logger.info("Setting feature lock state in storage: unlocked=$unlocked")
        context.storage.put("feature_unlocked", JsonPrimitive(unlocked))
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
}
