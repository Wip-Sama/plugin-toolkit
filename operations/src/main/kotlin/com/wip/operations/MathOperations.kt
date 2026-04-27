package com.wip.operations

import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.PluginModule
import com.wip.plugin.api.annotations.PluginParam

@PluginModule(
    id = "com.wip.operations.math",
    name = "Math Operations",
    version = "1.2.0",
    description = "A module that provides mathematical operations on lists of numbers."
)
class MathProcessor {
    @Capability(name = "sum", description = "Calculates the sum of a list of numbers")
    fun sumCapability(
        @PluginParam(description = "List of numbers to add") values: List<Double>
    ): Double = values.sum()

    @Capability(name = "multiply", description = "Calculates the product of a list of numbers")
    fun multiplyCapability(@PluginParam(description = "List of numbers to multiply") values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.reduce { acc, d -> acc * d }

    @Capability(name = "subtract", description = "Subtracts numbers sequentially")
    fun subtractCapability(
        @PluginParam(description = "Numerator") a: Double,
        @PluginParam(description = "Denominator") b: Double
    ): Double {
        return a - b
    }

    @Capability(name = "divide", description = "Divides two numbers")
    fun divideCapability(
        @PluginParam(description = "Numerator") a: Double,
        @PluginParam(description = "Denominator") b: Double
    ): Double {
        if (b == 0.0) throw ArithmeticException("Division by zero")
        return a / b
    }

    @Capability(name = "slow_sum", description = "Calculates the sum of a list of numbers with a delay")
    fun slowSumCapability(
        @PluginParam(description = "List of numbers to add") values: List<Double>,
        @PluginParam(description = "Delay in milliseconds") delay: Long
    ): Double {
        Thread.sleep(delay)
        return values.sum()
    }
}

val mathPluginModule = org.koin.dsl.module {
    single<com.wip.plugin.api.PluginEntry> { MathProcessorPluginEntry() }
}

