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
    val description: String
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class CapabilityParam(
    val description: String,
    val defaultValue: String = ""
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