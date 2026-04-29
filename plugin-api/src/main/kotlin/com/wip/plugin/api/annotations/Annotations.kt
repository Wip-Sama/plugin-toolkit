package com.wip.plugin.api.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginModule(
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