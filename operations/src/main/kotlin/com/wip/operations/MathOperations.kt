package com.wip.operations

import org.wip.plugintoolkit.api.annotations.*
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.ProgressReporter
import kotlinx.coroutines.delay
import org.wip.plugintoolkit.api.PluginContext

data class MathProcessorSettings(
    @PluginSetting(
        description = "API Key for Google services",
        defaultValue = "null",
    )
    val googleApiKey: String = "null",

    @PluginSetting(
        description = "Secure token for the operations server",
        required = true,
        secret = true
    )
    val serverToken: String = "",

    @PluginSetting(
        description = "Name of the person performing the operations",
        required = true
    )
    val operatorName: String = ""
)

@PluginInfo(
    id = "com.wip.operations.math",
    name = "Math Operations",
    version = "1.4.0",
    description = "A module that provides mathematical operations on lists of numbers."
)
class MathProcessor(val settings: MathProcessorSettings) {


    @PluginValidate()
    fun validate(
        logger: PluginLogger,
        pluginContext: PluginContext
    ): Result<Unit> {
        if (settings.googleApiKey != "null") return Result.success(Unit)
        return Result.failure(Exception("Validation failed"))
    }

    @PluginAction(name = "Reset Statistics", description = "Resets all internal math counters and history.")
    suspend fun resetStats(logger: PluginLogger) {
        logger.info("Resetting statistics...")
        delay(2000) // Simulate work
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
}