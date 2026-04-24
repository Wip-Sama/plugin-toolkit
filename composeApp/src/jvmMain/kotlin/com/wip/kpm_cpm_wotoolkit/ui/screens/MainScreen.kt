package com.wip.kpm_cpm_wotoolkit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import com.wip.kpm_cpm_wotoolkit.Greeting
import com.wip.kpm_cpm_wotoolkit.DynamicModuleLoader
import com.wip.plugin.api.DataProcessor
import com.wip.plugin.api.PluginEntry
import com.wip.plugin.api.*
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.compose_multiplatform
import org.koin.mp.KoinPlatformTools

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray

class DisabledProcessor(private val reason: String) : DataProcessor {
    override suspend fun process(request: PluginRequest): Result<PluginResponse> {
        return Result.failure(Exception("Processor Disabled: $reason"))
    }
}

@Composable
fun MainScreen() {
    var showContent by remember { mutableStateOf(false) }
    var jarPath by remember { mutableStateOf("C:\\Users\\sgroo\\AndroidStudioProjects\\CMP_desktop_test\\operations\\build\\libs\\operations.jar") }
    var moduleStatus by remember { mutableStateOf("Not Loaded") }
    var manifest by remember { mutableStateOf<PluginManifest?>(null) }
    var calculationResult by remember { mutableStateOf("") }
    var inputValues by remember { mutableStateOf("10, 20, 30") }
    var operation by remember { mutableStateOf("sum") }
    
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Plugin Architecture Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = jarPath,
            onValueChange = { jarPath = it },
            label = { Text("Plugin JAR Path") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val result = DynamicModuleLoader.loadPlugin(
                    jarPath = jarPath,
                    moduleClassName = "com.wip.operations.MathOperationsKt",
                    modulePropertyName = "mathPluginModule"
                )
                if (result.isSuccess) {
                    scope.launch {
                        val plugin = result.getOrThrow()
                        val initResult = plugin.initialize()
                        if (initResult.isSuccess) {
                            manifest = plugin.getManifest()
                            moduleStatus = "Loaded: ${manifest?.module?.name}"
                        } else {
                            moduleStatus = "Init Failed"
                        }
                    }
                } else {
                    moduleStatus = "Load Failed: ${result.exceptionOrNull()?.message}"
                }
            }) {
                Text("Load & Init Plugin")
            }

            Button(onClick = {
                DynamicModuleLoader.unloadPlugin()
                moduleStatus = "Unloaded"
            }) {
                Text("Unload")
            }
        }

        Text("Status: $moduleStatus", style = MaterialTheme.typography.bodyMedium)
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        OutlinedTextField(
            value = inputValues,
            onValueChange = { inputValues = it },
            label = { Text("Input Values (comma separated)") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. 10, 20, 30") }
        )
        
        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            manifest?.capabilities?.forEach { capability ->
                FilterChip(
                    selected = operation == capability.name,
                    onClick = { operation = capability.name },
                    label = { Text(capability.name) }
                )
            }
        }

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                scope.launch {
                    try {
                        val plugin = DynamicModuleLoader.getPlugin()
                        val processor = plugin?.getProcessor() ?: DisabledProcessor("No plugin loaded")
                        
                        // Parse list from input
                        val numericValues = inputValues.split(",")
                            .mapNotNull { it.trim().toDoubleOrNull() }

                        // Construct generic request
                        val request = PluginRequest(
                            method = operation,
                            parameters = mapOf(
                                "values" to JsonArray(numericValues.map { JsonPrimitive(it) })
                            )
                        )
                        
                        // Strict threading: Ensure processing happens on Default
                        val result = withContext(Dispatchers.Default) {
                            processor.process(request)
                        }
                        
                        if (result.isSuccess) {
                            val response = result.getOrThrow()
                            calculationResult = "Result: ${response.result}"
                        } else {
                            calculationResult = "Error: ${result.exceptionOrNull()?.message}"
                        }
                    } catch (e: Exception) {
                        calculationResult = "Crash: ${e.message}"
                    }
                }
            }
        ) {
            Text("Process (Agnostic Payload)")
        }

        Text(calculationResult, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { showContent = !showContent }) {
            Text(if (showContent) "Hide UI Demo" else "Show UI Demo")
        }

        AnimatedVisibility(showContent) {
            val greeting = remember { Greeting().greet() }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.compose_multiplatform), null)
                Text("Compose: $greeting")
            }
        }
    }
}
