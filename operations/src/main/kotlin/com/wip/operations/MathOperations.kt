package com.wip.operations

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.annotations.Capability
import org.wip.plugintoolkit.api.annotations.CapabilityContext
import org.wip.plugintoolkit.api.annotations.CapabilityOutput
import org.wip.plugintoolkit.api.annotations.CapabilityParam
import org.wip.plugintoolkit.api.annotations.PluginAction
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.annotations.PluginSetting
import org.wip.plugintoolkit.api.annotations.PluginValidate
import org.wip.plugintoolkit.api.annotations.RequiresSetting
import org.wip.plugintoolkit.api.annotations.ResumeState

data class MathProcessorSettings(
    @PluginSetting(
        description = "API Key for Google services",
        defaultValue = "null",
    ) val googleApiKey: String = "null",

    @PluginSetting(
        description = "Secure token for the operations server",
        required = true,
        secret = true
    ) val serverToken: String = "",

    @PluginSetting(
        description = "Name of the person performing the operations",
        required = true
    ) val operatorName: String = ""
)

@Serializable
data class DivisionResult(
    @CapabilityOutput(name = "quotient", description = "The quotient of the division") val quotient: Int,
    @CapabilityOutput(name = "remainder", description = "The remainder of the division") val remainder: Int
)

enum class VoiceOption {
    @RequiresSetting(["googleApiKey"])
    GOOGLE,

    @RequiresSetting(["serverToken"])
    SERVER, LOCAL
}

@Serializable
data class ComplexObject(
    val id: String, val properties: Map<String, String>, val metadata: Map<String, Int>
)

@Serializable
data class DataPoint(
    val x: Double, val y: Double, val label: String
)

@PluginInfo(
    id = "com.wip.operations.math",
    name = "Math Operations",
    version = "1.4.0",
    description = "A module that provides mathematical operations on lists of numbers.",
    supportedOs = [OS.WINDOWS, OS.LINUX, OS.MACOS]
)
class MathProcessor(val settings: MathProcessorSettings) {
    @PluginValidate()
    fun validate(
        logger: PluginLogger, pluginContext: PluginContext
    ): Result<Unit> {
        if (settings.googleApiKey != "null") return Result.success(Unit)
        return Result.failure(Exception("Validation failed"))
    }

    @PluginAction(name = "Reset Statistics", description = "Resets all internal math counters and history.")
    suspend fun resetStats(logger: PluginLogger) {
        logger.info("Resetting statistics...")
        delay(2000)
        logger.info("Statistics reset successfully.")
    }

    @PluginAction(name = "Run Diagnostics", description = "Checks the health of the math engine.")
    suspend fun runDiagnostics(logger: PluginLogger, progress: ProgressReporter) {
        logger.info("Starting diagnostics...")
        for (i in 1..5) {
            progress.report(i / 5f)
            delay(500)
            logger.info("Step $i complete")
        }
        logger.info("Diagnostics finished.")
    }

    @Capability(name = "sum", description = "Calculates the sum of a list of numbers")
    fun sumCapability(
        @CapabilityParam(description = "List of numbers to add") values: List<Double>
    ): Double = values.sum()

    @Capability(name = "multiply", description = "Calculates the product of a list of numbers")
    fun multiplyCapability(@CapabilityParam(description = "List of numbers to multiply") values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.reduce { acc, d -> acc * d }

    @Capability(name = "subtract", description = "Subtracts numbers sequentially")
    fun subtractCapability(
        @CapabilityParam(description = "Numerator", defaultValue = "0.0") a: Double,
        @CapabilityParam(description = "Denominator", defaultValue = "0.0") b: Double
    ): Double {
        return a - b
    }

    @Capability(name = "divide", description = "Divides two numbers")
    fun divideCapability(
        @CapabilityParam(description = "Numerator", defaultValue = "1.0") a: Double,
        @CapabilityParam(description = "Denominator", defaultValue = "1.0") b: Double
    ): Double {
        if (b == 0.0) throw ArithmeticException("Division by zero")
        return a / b
    }

    @Capability(name = "slow_sum", description = "Calculates the sum of a list of numbers with a delay")
    fun slowSumCapability(
        @CapabilityParam(description = "List of numbers to add") values: List<Double>,
        @CapabilityParam(description = "Delay in milliseconds", defaultValue = "1000") delay: Long
    ): Double {
        Thread.sleep(delay)
        return values.sum()
    }

    @Capability(name = "integer_divide", description = "Divides two integers and returns the quotient and remainder")
    fun integerDivideCapability(
        @CapabilityParam(description = "Dividend") a: Int, @CapabilityParam(description = "Divisor") b: Int
    ): DivisionResult {
        if (b == 0) throw ArithmeticException("Division by zero")
        return DivisionResult(a / b, a % b)
    }

    @Capability(name = "formatted_sum", description = "Returns the sum formatted as text")
    @CapabilityOutput(
        name = "formattedResult",
        description = "The sum formatted as currency",
        semanticTypes = ["text/plain"]
    )
    fun formattedSumCapability(
        @CapabilityParam(description = "Values to sum") values: List<Double>
    ): String {
        return "$${values.sum()}"
    }

    @Capability(
        name = "slow_pausable_sum",
        description = "Calculates the sum of a list of numbers slowly with pause support",
        supportsPause = true
    )
    suspend fun slowPausableSum(
        @CapabilityParam(description = "List of numbers to add") values: List<Double>,
        @CapabilityParam(description = "Delay in milliseconds between steps", defaultValue = "500") stepDelay: Long,
        @ResumeState resumeState: JsonElement?,
        context: PluginContext
    ): ExecutionResult {
        val state = resumeState?.let {
            try {
                Json.decodeFromJsonElement(SumProgressState.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }
        var currentIndex = state?.currentIndex ?: 0
        var runningSum = state?.runningSum ?: 0.0
        var isPaused = false
        context.onSignal { signal ->
            if (signal == PluginSignal.PAUSE) {
                isPaused = true
            }
        }
        while (currentIndex < values.size) {
            if (isPaused) {
                context.logger.info("slow_pausable_sum paused at index $currentIndex with running sum $runningSum")
                val pausedState = SumProgressState(currentIndex, runningSum)
                return ExecutionResult.Paused(Json.encodeToJsonElement(SumProgressState.serializer(), pausedState))
            }
            runningSum += values[currentIndex]
            context.progress.report((currentIndex + 1).toFloat() / values.size)
            context.logger.info("slow_pausable_sum processed index $currentIndex: value=${values[currentIndex]}, runningSum=$runningSum")
            currentIndex++
            delay(stepDelay)
        }
        context.logger.info("slow_pausable_sum completed with result $runningSum")
        return ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(runningSum)))
    }

    @Capability(
        name = "slow_pausable_factorial",
        description = "Calculates the factorial of a number slowly with pause support",
        supportsPause = true
    )
    suspend fun slowPausableFactorial(
        @CapabilityParam(description = "Number to compute factorial for") n: Int,
        @CapabilityParam(description = "Delay in milliseconds between steps", defaultValue = "500") stepDelay: Long,
        @ResumeState resumeState: JsonElement?,
        context: PluginContext
    ): ExecutionResult {
        if (n < 0) {
            return ExecutionResult.Error("Factorial of negative number is undefined")
        }
        val state = resumeState?.let {
            try {
                Json.decodeFromJsonElement(FactorialProgressState.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }
        var currentN = state?.currentN ?: 2
        var runningProduct = state?.runningProduct ?: 1L
        var isPaused = false
        context.onSignal { signal ->
            if (signal == PluginSignal.PAUSE) {
                isPaused = true
            }
        }
        val target = n.coerceAtLeast(1)
        while (currentN <= target) {
            if (isPaused) {
                context.logger.info("slow_pausable_factorial paused at multiplier $currentN with running product $runningProduct")
                val pausedState = FactorialProgressState(currentN, runningProduct)
                return ExecutionResult.Paused(
                    Json.encodeToJsonElement(
                        FactorialProgressState.serializer(),
                        pausedState
                    )
                )
            }
            runningProduct *= currentN
            context.progress.report((currentN - 1).toFloat() / (target - 1).coerceAtLeast(1))
            context.logger.info("slow_pausable_factorial multiplied by $currentN: runningProduct=$runningProduct")
            currentN++
            delay(stepDelay)
        }
        context.logger.info("slow_pausable_factorial completed with result $runningProduct")
        return ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(runningProduct)))
    }

    @Capability(name = "regex_validate_ip", description = "Checks if the provided IP address is valid using regex")
    fun regexValidateIp(
        @CapabilityParam(
            description = "IP address to validate",
            regex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        ) ip: String
    ): Boolean {
        return true
    }

    @Capability(
        name = "sum_from_file",
        description = "Reads a txt file where each row contains a number, and sums them up"
    )
    fun sumFromFile(
        @CapabilityParam(description = "Path to the text file", semanticTypes = ["file/txt"]) filePath: String
    ): Double {
        val file = java.io.File(filePath)
        if (!file.exists()) throw IllegalArgumentException("File does not exist: $filePath")
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { it.toDoubleOrNull() }.sum()
    }

    @Capability(name = "invert_color", description = "Inverts a color string in rgb(r, g, b) format")
    @CapabilityOutput(
        name = "invertedColor",
        description = "The inverted color in rgb(r, g, b) format",
        semanticTypes = ["color/rgb"]
    )
    fun invertColor(
        @CapabilityParam(description = "Input color in rgb(r,g,b) format", semanticTypes = ["color/rgb"]) color: String
    ): String {
        val regex = Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
        val match = regex.find(color) ?: throw IllegalArgumentException("Invalid color format. Expected rgb(r,g,b)")
        val r = match.groupValues[1].toInt().coerceIn(0, 255)
        val g = match.groupValues[2].toInt().coerceIn(0, 255)
        val b = match.groupValues[3].toInt().coerceIn(0, 255)
        return "rgb(${255 - r}, ${255 - g}, ${255 - b})"
    }

    @Capability(name = "multi_semantic_op", description = "Test capability with multiple semantic types")
    @CapabilityOutput(
        name = "output_data",
        description = "Multi-typed output",
        semanticTypes = ["color/rgb", "file/txt"]
    )
    fun multiSemanticOp(
        @CapabilityParam(
            description = "Multi-typed parameter",
            semanticTypes = ["color/rgb", "file/txt"]
        ) inputVal: String
    ): String {
        return inputVal
    }

    @Capability(
        name = "advanced_generation",
        description = "Test capability for advanced features",
        context = CapabilityContext.FLOW_ONLY,
        requiresSettings = ["operatorName"]
    )
    fun advancedGeneration(
        @CapabilityParam(description = "Voice to use") voice: VoiceOption,
        @CapabilityParam(description = "Configuration map") config: Map<String, Double>
    ): ComplexObject {
        return ComplexObject(
            id = "test-123",
            properties = mapOf("voice" to voice.name),
            metadata = config.mapValues { it.value.toInt() })
    }

    @Capability(name = "process_complex_object", description = "Processes a complex object by appending a suffix to its ID")
    fun processComplexObject(
        @CapabilityParam(description = "The complex object to process") obj: ComplexObject
    ): ComplexObject {
        return obj.copy(
            id = obj.id + "-processed",
            properties = obj.properties + ("processed" to "true")
        )
    }

    @Capability(name = "merge_complex_objects", description = "Merges a list of complex objects into one")
    fun mergeComplexObjects(
        @CapabilityParam(description = "List of complex objects to merge") objects: List<ComplexObject>
    ): ComplexObject {
        if (objects.isEmpty()) return ComplexObject("empty", emptyMap(), emptyMap())
        val mergedProperties = objects.flatMap { it.properties.entries }.associate { it.key to it.value }
        val mergedMetadata = objects.flatMap { it.metadata.entries }.associate { it.key to it.value }
        return ComplexObject(
            id = objects.joinToString("-") { it.id },
            properties = mergedProperties,
            metadata = mergedMetadata
        )
    }

    @Capability(name = "process_data_points", description = "Calculates the centroid of data points")
    fun processDataPoints(
        @CapabilityParam(description = "List of data points") points: List<DataPoint>
    ): DataPoint {
        if (points.isEmpty()) return DataPoint(0.0, 0.0, "empty")
        val sumX = points.sumOf { it.x }
        val sumY = points.sumOf { it.y }
        return DataPoint(sumX, sumY, "centroid")
    }
}

@Serializable
data class SumProgressState(
    val currentIndex: Int, val runningSum: Double
)

@Serializable
data class FactorialProgressState(
    val currentN: Int, val runningProduct: Long
)