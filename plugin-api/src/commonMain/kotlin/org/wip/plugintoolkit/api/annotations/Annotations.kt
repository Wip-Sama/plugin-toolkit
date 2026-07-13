package org.wip.plugintoolkit.api.annotations

import org.wip.plugintoolkit.api.OS

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val minMemoryMb: Int = 128,
    val minExecutionTimeMs: Int = 10,
    val supportedOs: Array<OS> = []
)

/**
 * Provides metadata for a capability result.
 * Can be applied to a single-return capability function or to properties of a custom data class return type.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class CapabilityResult(
    val name: String = "",
    val description: String = "",
    val semanticTypes: Array<String> = []
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
 * @property context Specifies whether the capability runs only in flows, alone, or both.
 * @property requiresSettings Settings required for this capability to be enabled.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Capability(
    val name: String,
    val description: String,
    val supportsPause: Boolean = false,
    val supportsCancel: Boolean = true,
    val context: CapabilityContext = CapabilityContext.ANY,
    @Deprecated("Use @RequiresSetting on the capability function instead")
    val requiresSettings: Array<String> = []
)

/**
 * Specifies the context in which a capability can be executed.
 */
enum class CapabilityContext {
    ANY, FLOW_ONLY, STANDALONE_ONLY
}


/**
 * Indicates that the annotated element requires specific plugin settings to be configured before it can be used.
 * Can be applied to enum entries or capability parameters/functions.
 * 
 * **Behavioral Note:** Any setting specified in `@RequiresSetting` (or within a Capability's `requiresSettings`)
 * is treated as a context-specific requirement. This means that even if the setting is non-nullable or explicitly 
 * marked with `@PluginSetting(required = true)`, it will **not** prevent the plugin from loading globally. 
 * Instead, the specific capability or enum option will be locked/disabled until the user configures the setting.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresSetting(
    val settings: Array<String>
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
    val maxChoices: Int = -1,
    val required: Boolean = false,
    val secret: Boolean = false,
    val semanticTypes: Array<String> = [],
    val pathTemplate: String = ""
)

/**
 * Marks a parameter as an input location (file or directory).
 * This implicitly grants read access (readsFiles = true) to the capability.
 *
 * @property description A description of the input location parameter.
 * @property defaultValue The default path.
 * @property required Whether the parameter is mandatory.
 * @property semanticTypes Semantic types (e.g., "file/txt", "directory").
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class CapabilityInput(
    val description: String,
    val defaultValue: String = "",
    val required: Boolean = false,
    val semanticTypes: Array<String> = []
)

/**
 * Marks a parameter as an output location (file or directory).
 * This implicitly grants write access (writesFiles = true) to the capability.
 *
 * @property description A description of the output location parameter.
 * @property defaultValue The default path.
 * @property required Whether the parameter is mandatory.
 * @property semanticTypes Semantic types (e.g., "file/txt", "directory").
 * @property autogeneratedPattern An optional pattern to generate the path automatically from other parameters (e.g., "{inputPath}/../../out.txt").
 * @property isDestructive True if the capability might perform destructive operations (like overwrite/delete) at this location.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class CapabilityOutput(
    val description: String,
    val defaultValue: String = "",
    val required: Boolean = false,
    val semanticTypes: Array<String> = [],
    val autogeneratedPattern: String = "",
    val isDestructive: Boolean = false
)

/**
 * Marks a property as a plugin setting.
 *
 * Settings are persisted by the host application and can be modified by the user
 * in the plugin configuration UI.
 *
 * @property description A description of the setting.
 * @property defaultValue The default value for the setting (as a string).
 * @property required Whether the setting is mandatory for the plugin to function.
 * @property secret Whether the setting contains sensitive information (e.g., API keys).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginSetting(
    val description: String,
    val defaultValue: String = "",
    val required: Boolean = false,
    val secret: Boolean = false,
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
 * Marks a function to be called during the plugin's load phase.
 *
 * This function is executed after [org.wip.plugintoolkit.api.PluginEntry.initialize]
 * but before any validation or capability execution.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginLoad

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
 * Marks a function to be called during the plugin's update phase.
 *
 * This function is executed when a new version of the plugin is installed over
 * an existing one.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginUpdate

/**
 * Marks a parameter to receive the saved resume state for a pausable capability.
 *
 * The parameter must be of type `JsonElement?`. If the capability is being resumed,
 * this parameter will contain the state that was passed to [org.wip.plugintoolkit.api.ExecutionResult.Paused].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
annotation class ResumeState

/**
 * Provides metadata for a complex object parameter or return type.
 *
 * This annotation allows for better identification and versioning of complex objects,
 * especially when multiple plugins might define objects with the same name.
 *
 * @property id An optional identifier for the complex object. Defaults to the class name if empty.
 * @property description A description of the complex object.
 * @property version The version of the complex object structure.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ComplexObject(
    val id: String = "",
    val description: String = "",
    val version: Int = 1
)