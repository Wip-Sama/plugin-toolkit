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
import org.wip.plugintoolkit.api.annotations.PluginSetting
import org.wip.plugintoolkit.api.annotations.PluginValidate
import org.wip.plugintoolkit.api.annotations.RequiresSetting
import org.wip.plugintoolkit.api.annotations.ResumeState
import java.io.File

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
    ) val userId: String? = "user_demo"
)

@Serializable
data class ExampleProgressState(
    val currentStep: Int,
    val totalSteps: Int,
    val accumulatedValue: String
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
    LOCAL
}

@PluginInfo(
    id = "org.wip.complete",
    name = "Complete Example Plugin",
    version = "1.0.0",
    description = "Complete showcase of plugin API features including settings, validation, signals, storage, file system, and flow contexts.",
    supportedOs = [OS.WINDOWS, OS.LINUX, OS.MACOS]
)
class CompleteExamplePlugin(val settings: CompleteExampleSettings) {

    @PluginValidate
    fun validate(logger: PluginLogger, pluginContext: PluginContext): Result<Unit> {
        logger.info("Validating CompleteExamplePlugin settings...")
        if (settings.secretToken.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("secretToken must not be blank"))
        }
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
}
