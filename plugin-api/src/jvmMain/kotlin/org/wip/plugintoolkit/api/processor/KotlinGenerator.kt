package org.wip.plugintoolkit.api.processor

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import org.wip.plugintoolkit.api.processor.GeneratorUtils.hasQualifiedName
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_SETUP_ANNOTATION
import org.wip.plugintoolkit.api.processor.ProcessorConstants.PLUGIN_UPDATE_ANNOTATION
import org.wip.plugintoolkit.api.processor.generators.ActionRegistryGenerator
import org.wip.plugintoolkit.api.processor.generators.DispatcherGenerator
import org.wip.plugintoolkit.api.processor.generators.EntryGenerator
import org.wip.plugintoolkit.api.processor.generators.ManifestGenerator

object KotlinGenerator {
    fun generate(
        packageName: String,
        baseClassName: String,
        id: String,
        name: String,
        version: String,
        description: String,
        minMemoryMb: Int,
        minExecutionTimeMs: Int,
        functions: List<KSFunctionDeclaration>,
        settingsProperties: List<KSPropertyDeclaration>,
        actions: List<KSFunctionDeclaration>,
        classDeclaration: KSClassDeclaration
    ): FileSpec {
        val generatedFileName = baseClassName + "Generated"
        val manifestName = baseClassName + "Manifest"
        val dispatcherName = baseClassName + "Dispatcher"
        val entryName = baseClassName + "PluginEntry"

        val fileSpec = FileSpec.builder(packageName, generatedFileName)

        // 1. Generate Manifest Object
        val setupFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_SETUP_ANNOTATION) } }
        val updateFunction = classDeclaration.getAllFunctions().find { it.annotations.any { ann -> ann.hasQualifiedName(PLUGIN_UPDATE_ANNOTATION) } }
        
        val manifestType = ManifestGenerator.generateManifestObject(
            manifestName, id, name, version, description, 
            minMemoryMb, minExecutionTimeMs, functions, 
            settingsProperties, actions, updateFunction != null, setupFunction != null
        )
        fileSpec.addType(manifestType)

        // 2. Generate Dispatcher Class
        val dispatcherType = DispatcherGenerator.generateDispatcherClass(
            dispatcherName, packageName, baseClassName, functions, actions
        )
        fileSpec.addType(dispatcherType)

        // 3. Generate Entry Class
        val entryType = EntryGenerator.generateEntryClass(
            entryName, packageName, baseClassName, manifestName, dispatcherName, classDeclaration
        )
        fileSpec.addType(entryType)

        // 4. Generate Action Registry
        val registryName = baseClassName + "Actions"
        val registryType = ActionRegistryGenerator.generateActionRegistry(registryName, actions)
        fileSpec.addType(registryType)

        return fileSpec.build()
    }
}
