package org.wip.plugintoolkit.api.annotations

/**
 * Marks a class as the main entry point for a plugin.
 *
 * A class annotated with `@PluginInfo` must also implement [org.wip.plugintoolkit.api.DataProcessor].
 * The KSP processor will generate the necessary [org.wip.plugintoolkit.api.PluginEntry] implementation and
 * ServiceLoader registration.
 *
 * @property id A unique identifier for the plugin (e.g., "my-plugin").
 * @property name A human-readable name for the plugin.
 * @property version The semantic version of the plugin.
 * @property description A brief summary of what the plugin does.
 * @property minMemoryMb The minimum amount of memory (in MB) required to run the plugin.
 * @property minExecutionTimeMs The estimated minimum execution time for a typical task.
 */
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

/**
 * Marks a function as a plugin capability.
 *
 * Capabilities are exposed to the host application as executable tasks.
 * The function must be a `suspend` function.
 *
 * @property name The name of the capability.
 * @property description A description of what the capability does.
 * @property supportsPause Whether the capability supports being paused and resumed.
 * @property supportsCancel Whether the capability supports being cancelled.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Capability(
    val name: String,
    val description: String,
    val supportsPause: Boolean = false,
    val supportsCancel: Boolean = true
)

/**
 * Provides metadata for a capability parameter.
 *
 * This information is used by the host application to generate appropriate UI controls
 * and perform input validation.
 *
 * @property description A description of the parameter.
 * @property defaultValue The default value for the parameter (as a string).
 * @property minValue The minimum allowed numeric value (if applicable).
 * @property maxValue The maximum allowed numeric value (if applicable).
 * @property minLength The minimum allowed string length (if applicable).
 * @property maxLength The maximum allowed string length (if applicable).
 * @property regex A regular expression for validating the input string.
 */
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

/**
 * Marks a property as a plugin setting.
 *
 * Settings are persisted by the host application and can be modified by the user
 * in the plugin configuration UI.
 *
 * @property description A description of the setting.
 * @property defaultValue The default value for the setting (as a string).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginSetting(
    val description: String,
    val defaultValue: String = ""
)

/**
 * Marks a function to be called during the plugin's setup phase.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginSetup

/**
 * Marks a function to be called during the plugin's validation phase.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginValidate

/**
 * Marks a function as a custom action for the plugin.
 *
 * Actions are simple functions that can be triggered from the UI.
 * The function must be a `suspend` function and accept a `PluginContext`.
 *
 * @property name The human-readable name of the action.
 * @property description A brief description of what the action does.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginAction(
    val name: String,
    val description: String
)

/**
 * Marks a parameter to receive the saved resume state for a pausable capability.
 *
 * The parameter must be of type `JsonElement?`. If the capability is being resumed,
 * this parameter will contain the state that was passed to [org.wip.plugintoolkit.api.ExecutionResult.Paused].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class ResumeState