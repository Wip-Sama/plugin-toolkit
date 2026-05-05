package com.wip.operations

import org.wip.plugintoolkit.api.annotations.Capability
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.annotations.CapabilityParam

@PluginInfo(
    id = "com.wip.operations.math",
    name = "Math Operations",
    version = "1.3.1",
    description = "A module that provides mathematical operations on lists of numbers."
)
class MathProcessor {
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