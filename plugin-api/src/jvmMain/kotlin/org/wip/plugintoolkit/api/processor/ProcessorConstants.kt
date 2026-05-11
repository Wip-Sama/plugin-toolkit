package org.wip.plugintoolkit.api.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.asClassName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.*

object ProcessorConstants {
    const val API_PACKAGE = "org.wip.plugintoolkit.api"
    const val ANNOTATION_PACKAGE = "$API_PACKAGE.annotations"
    
    // Annotations
    const val PLUGIN_INFO_ANNOTATION = "$ANNOTATION_PACKAGE.PluginInfo"
    const val CAPABILITY_ANNOTATION = "$ANNOTATION_PACKAGE.Capability"
    const val CAPABILITY_PARAM_ANNOTATION = "$ANNOTATION_PACKAGE.CapabilityParam"
    const val PLUGIN_SETTING_ANNOTATION = "$ANNOTATION_PACKAGE.PluginSetting"
    const val PLUGIN_ACTION_ANNOTATION = "$ANNOTATION_PACKAGE.PluginAction"
    const val RESUME_STATE_ANNOTATION = "$ANNOTATION_PACKAGE.ResumeState"
    const val PLUGIN_SETUP_ANNOTATION = "$ANNOTATION_PACKAGE.PluginSetup"
    const val PLUGIN_VALIDATE_ANNOTATION = "$ANNOTATION_PACKAGE.PluginValidate"
    const val PLUGIN_LOAD_ANNOTATION = "$ANNOTATION_PACKAGE.PluginLoad"
    const val PLUGIN_UPDATE_ANNOTATION = "$ANNOTATION_PACKAGE.PluginUpdate"

    // API Classes
    val CN_PLUGIN_MANIFEST = PluginManifest::class.asClassName()
    val CN_PLUGIN_INFO = PluginInfo::class.asClassName()
    val CN_REQUIREMENTS = Requirements::class.asClassName()
    val CN_CAPABILITY = Capability::class.asClassName()
    val CN_PLUGIN_ACTION = PluginAction::class.asClassName()
    val CN_PARAMETER_METADATA = ParameterMetadata::class.asClassName()
    val CN_PARAMETER_CONSTRAINTS = ParameterConstraints::class.asClassName()
    val CN_DATA_PROCESSOR = DataProcessor::class.asClassName()
    val CN_PLUGIN_REQUEST = PluginRequest::class.asClassName()
    val CN_PLUGIN_RESPONSE = PluginResponse::class.asClassName()
    val CN_PLUGIN_ENTRY = PluginEntry::class.asClassName()
    val CN_SETTING_METADATA = SettingMetadata::class.asClassName()
    val CN_JOB_HANDLE = JobHandle::class.asClassName()
    val CN_PLUGIN_SIGNAL = PluginSignal::class.asClassName()
    val CN_PLUGIN_CONTEXT = PluginContext::class.asClassName()
    val CN_PLUGIN_LOGGER = PluginLogger::class.asClassName()
    val CN_PLUGIN_FILESYSTEM = PluginFileSystem::class.asClassName()
    val CN_PROGRESS_REPORTER = ProgressReporter::class.asClassName()
    val CN_EXECUTION_RESULT = ExecutionResult::class.asClassName()
    val CN_EXECUTION_RESULT_SUCCESS = ExecutionResult.Success::class.asClassName()
    val CN_EXECUTION_RESULT_ERROR = ExecutionResult.Error::class.asClassName()
    
    // Common Types
    val CN_MAP = ClassName("kotlin.collections", "Map")
    val CN_LIST = ClassName("kotlin.collections", "List")
    val CN_RESULT = ClassName("kotlin", "Result")
    val CN_UNIT = Unit::class.asClassName()
    val CN_ILLEGAL_ARGUMENT_EXCEPTION = ClassName("kotlin", "IllegalArgumentException")
    val CN_ILLEGAL_STATE_EXCEPTION = ClassName("kotlin", "IllegalStateException")

    // Functions and Members
    val MN_GET_DATA_TYPE = MemberName(API_PACKAGE, "getDataType")
    val CN_JSON = Json::class.asClassName()
    val MN_DECODE_FROM_JSON_ELEMENT = MemberName("kotlinx.serialization.json", "decodeFromJsonElement")
    val MN_ENCODE_FROM_JSON_ELEMENT = MemberName("kotlinx.serialization.json", "encodeToJsonElement")
    
    val CN_COROUTINE_SCOPE = CoroutineScope::class.asClassName()
    val CN_DISPATCHERS = Dispatchers::class.asClassName()
    val MN_SUPERVISOR_JOB = MemberName("kotlinx.coroutines", "SupervisorJob")
    val MN_ASYNC = MemberName("kotlinx.coroutines", "async")
    val MN_LAUNCH = MemberName("kotlinx.coroutines", "launch")
    val MN_CANCEL = MemberName("kotlinx.coroutines", "cancel")

    val MN_SIGNAL_PAUSE = MemberName(CN_PLUGIN_SIGNAL, "PAUSE")
    val MN_SIGNAL_CANCEL = MemberName(CN_PLUGIN_SIGNAL, "CANCEL")

    // Infrastructure Types for comparison
    val INFRASTRUCTURE_TYPES = setOf(
        CN_PLUGIN_LOGGER,
        CN_PROGRESS_REPORTER,
        CN_PLUGIN_FILESYSTEM,
        CN_PLUGIN_CONTEXT
    )
}
