package com.wip.plugin.api.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val minMemoryMb: Int = 128,
    val minExecutionTimeMs: Int = 10
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Capability(
    val name: String,
    val description: String,
    val supportsPause: Boolean = false,
    val supportsCancel: Boolean = true
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class CapabilityParam(
    val description: String,
    val defaultValue: String = "",
    val minValue: Double = Double.NaN,
    val maxValue: Double = Double.NaN,
    val minLength: Int = -1,
    val maxLength: Int = -1,
    val regex: String = "",
    val multiSelect: Boolean = false,
    val minChoices: Int = -1,
    val maxChoices: Int = -1
)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginSetting(
    val description: String,
    val defaultValue: String = ""
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginSetup

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginValidate