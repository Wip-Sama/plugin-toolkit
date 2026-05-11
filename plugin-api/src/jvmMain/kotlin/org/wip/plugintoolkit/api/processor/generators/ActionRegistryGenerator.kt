package org.wip.plugintoolkit.api.processor.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_ACTION_ANNOTATION

object ActionRegistryGenerator {
    fun generateActionRegistry(registryName: String, actions: List<KSFunctionDeclaration>): TypeSpec {
        val registryType = TypeSpec.objectBuilder(registryName)
        
        actions.forEach { func ->
            val ann = func.annotations.first { it.hasQualifiedName(PLUGIN_ACTION_ANNOTATION) }
            val actName = ann.arguments.find { it.name?.asString() == "name" }?.value as String
            val constName = actName.uppercase().replace(" ", "_")
            
            registryType.addProperty(
                PropertySpec.builder(constName, String::class)
                    .addModifiers(KModifier.CONST)
                    .initializer("%S", func.simpleName.asString())
                    .build()
            )
        }
        
        return registryType.build()
    }
}
