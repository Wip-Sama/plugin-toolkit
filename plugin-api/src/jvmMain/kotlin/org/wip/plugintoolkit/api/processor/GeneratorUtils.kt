package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.*
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType

const val PLUGIN_INFO_ANNOTATION = "org.wip.plugintoolkit.api.annotations.PluginInfo"
const val CAPABILITY_ANNOTATION = "org.wip.plugintoolkit.api.annotations.Capability"
const val CAPABILITY_PARAM_ANNOTATION = "org.wip.plugintoolkit.api.annotations.CapabilityParam"
const val PLUGIN_SETTING_ANNOTATION = "org.wip.plugintoolkit.api.annotations.PluginSetting"
const val PLUGIN_ACTION_ANNOTATION = "org.wip.plugintoolkit.api.annotations.PluginAction"
const val RESUME_STATE_ANNOTATION = "org.wip.plugintoolkit.api.annotations.ResumeState"
const val PLUGIN_SETUP_ANNOTATION = "org.wip.plugintoolkit.api.annotations.PluginSetup"
const val PLUGIN_VALIDATE_ANNOTATION = "org.wip.plugintoolkit.api.annotations.PluginValidate"

object GeneratorUtils {
    fun mapKSTypeToDataType(ksType: KSType): DataType {
        val declaration = ksType.declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: ""
        
        return when (qualifiedName) {
            "kotlin.Double", "kotlin.Float" -> DataType.Primitive(PrimitiveType.DOUBLE)
            "kotlin.Int", "kotlin.Short", "kotlin.Byte", "kotlin.Long" -> DataType.Primitive(PrimitiveType.INT)
            "kotlin.String", "kotlin.Char" -> DataType.Primitive(PrimitiveType.STRING)
            "kotlin.Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
            "kotlin.Unit" -> DataType.Primitive(PrimitiveType.UNIT)
            "kotlinx.serialization.json.JsonElement", "kotlin.Any" -> DataType.Primitive(PrimitiveType.ANY)
            "kotlin.collections.List", "kotlin.collections.MutableList" -> {
                val elementType = ksType.arguments.firstOrNull()?.type?.resolve()
                if (elementType != null) {
                    DataType.Array(mapKSTypeToDataType(elementType))
                } else {
                    DataType.Primitive(PrimitiveType.ANY)
                }
            }
            else -> {
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
                    val options = declaration.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.classKind == ClassKind.ENUM_ENTRY }
                        .map { it.simpleName.asString() }
                        .toList()
                    DataType.Enum(qualifiedName, options)
                } else {
                    DataType.Object(qualifiedName)
                }
            }
        }
    }

    fun KSAnnotation.hasQualifiedName(name: String): Boolean {
        return this.annotationType.resolve().declaration.qualifiedName?.asString() == name
    }
}
