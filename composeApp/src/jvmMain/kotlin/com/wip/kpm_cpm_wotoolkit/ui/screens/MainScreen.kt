package com.wip.kpm_cpm_wotoolkit.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wip.kpm_cpm_wotoolkit.DynamicModuleLoader
import com.wip.kpm_cpm_wotoolkit.ui.components.*
import com.wip.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    var jarPath by remember { mutableStateOf("C:\\Users\\sgroo\\AndroidStudioProjects\\CMP_desktop_test\\operations\\build\\libs\\operations.jar") }
    var selectedPlugin by remember { mutableStateOf<PluginEntry?>(null) }
    var selectedCapability by remember { mutableStateOf<Capability?>(null) }
    var loadedPlugins by remember { mutableStateOf(DynamicModuleLoader.getPlugins()) }
    
    var executionResult by remember { mutableStateOf<Result<PluginResponse>?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    // Map to store parameter values as strings for the UI
    val parameterValues = remember { mutableStateMapOf<String, String>() }

    // Reset parameters when capability changes
    LaunchedEffect(selectedCapability) {
        parameterValues.clear()
        selectedCapability?.parameters?.forEach { (name, meta) ->
            parameterValues[name] = ""
        }
        executionResult = null
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // --- Sidebar ---
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Text(
                "Plugin Center",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(title = "LOAD NEW JAR", icon = Icons.Default.Add)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = jarPath,
                onValueChange = { jarPath = it },
                label = { Text("Path to .jar") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Button(
                onClick = {
                    scope.launch {
                        // Hardcoded for demo, in real app we'd scan or ask user
                        val result = DynamicModuleLoader.loadPlugin(
                            jarPath, 
                            "com.wip.operations.MathOperationsKt", 
                            "mathPluginModule"
                        )
                        if (result.isSuccess) {
                            val plugin = result.getOrThrow()
                            plugin.initialize()
                            loadedPlugins = DynamicModuleLoader.getPlugins()
                            selectedPlugin = plugin
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Load & Initialize")
            }
            Button(
                onClick = {
                    scope.launch {
                        DynamicModuleLoader.unloadPlugin(jarPath)
                        loadedPlugins = DynamicModuleLoader.getPlugins()
                        selectedPlugin = null
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Unload")
            }

            Spacer(modifier = Modifier.height(32.dp))
            SectionHeader(title = "LOADED MODULES", icon = Icons.Default.List)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(loadedPlugins) { plugin ->
                    val manifest = plugin.getManifest()
                    val isSelected = selectedPlugin == plugin
                    
                    Surface(
                        onClick = { 
                            selectedPlugin = plugin
                            selectedCapability = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsInputComponent,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    manifest.module.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "v${manifest.module.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Main Content ---
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (selectedPlugin == null) {
                EmptyState("Select a module to begin testing")
            } else {
                val manifest = selectedPlugin!!.getManifest()
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(32.dp)
                ) {
                    // Module Header
                    ModuleHeader(manifest)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Capabilities List
                        Column(modifier = Modifier.weight(0.4f)) {
                            SectionHeader(title = "CAPABILITIES", icon = Icons.Default.Bolt)
                            Spacer(modifier = Modifier.height(16.dp))
                            manifest.capabilities.forEach { capability ->
                                CapabilityItem(
                                    capability = capability,
                                    isSelected = selectedCapability == capability,
                                    onClick = { selectedCapability = capability }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(32.dp))
                        
                        // Tester Area
                        Column(modifier = Modifier.weight(0.6f)) {
                            AnimatedContent(
                                targetState = selectedCapability,
                                transitionSpec = {
                                    fadeIn() + slideInHorizontally { it / 2 } togetherWith fadeOut() + slideOutHorizontally { -it / 2 }
                                }
                            ) { capability ->
                                if (capability != null) {
                                    Column {
                                        CapabilityTester(
                                            capability = capability,
                                            parameterValues = parameterValues,
                                            isExecuting = isExecuting,
                                            onExecute = {
                                                scope.launch {
                                                    isExecuting = true
                                                    try {
                                                        val request = buildRequest(capability, parameterValues)
                                                        val processor = selectedPlugin!!.getProcessor()
                                                        val result = withContext(Dispatchers.Default) {
                                                            processor.process(request)
                                                        }
                                                        executionResult = result
                                                    } catch (e: Exception) {
                                                        executionResult = Result.failure(e)
                                                    } finally {
                                                        isExecuting = false
                                                    }
                                                }
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = executionResult != null,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            executionResult?.let { result ->
                                                Column {
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    if (result.isSuccess) {
                                                        ResponseView(result.getOrThrow())
                                                    } else {
                                                        ErrorView(result.exceptionOrNull()?.message ?: "Unknown error")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    EmptyState("Select a capability to test")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleHeader(manifest: PluginManifest) {
    Column {
        Text(
            manifest.module.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            manifest.module.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge { Text("ID: ${manifest.module.id}") }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) { 
                Text("Mem: ${manifest.requirements.minMemoryMb}MB") 
            }
        }
    }
}

@Composable
fun CapabilityItem(
    capability: Capability,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(capability.name, fontWeight = FontWeight.Bold)
            Text(
                capability.description, 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CapabilityTester(
    capability: Capability,
    parameterValues: MutableMap<String, String>,
    isExecuting: Boolean,
    onExecute: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Capability Tester: ${capability.name}", icon = Icons.Default.PlayArrow)
        Spacer(modifier = Modifier.height(16.dp))
        
        capability.parameters?.forEach { (name, meta) ->
            DynamicParameterInput(
                name = name,
                metadata = meta,
                value = parameterValues[name] ?: "",
                onValueChange = { parameterValues[name] = it }
            )
        }
        
        if (capability.parameters.isNullOrEmpty()) {
            Text("No parameters required for this capability.", style = MaterialTheme.typography.bodyMedium)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExecuting,
            shape = MaterialTheme.shapes.medium
        ) {
            if (isExecuting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Execute Capability")
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox, 
                contentDescription = null, 
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Text(
                message, 
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ErrorView(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun buildRequest(capability: Capability, values: Map<String, String>): PluginRequest {
    val params = mutableMapOf<String, JsonElement>()
    capability.parameters?.forEach { (name, meta) ->
        val stringValue = values[name] ?: ""
        params[name] = parseValue(stringValue, meta.type)
    }
    return PluginRequest(method = capability.name, parameters = params)
}

private fun parseValue(value: String, type: DataType): JsonElement {
    if (value.isBlank()) return JsonNull
    return when (type) {
        is DataType.Primitive -> {
            when (type.primitiveType) {
                PrimitiveType.DOUBLE -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
                PrimitiveType.INT -> JsonPrimitive(value.toIntOrNull() ?: 0)
                PrimitiveType.BOOLEAN -> JsonPrimitive(value.lowercase() == "true")
                PrimitiveType.STRING -> JsonPrimitive(value)
                else -> JsonPrimitive(value)
            }
        }
        is DataType.Array -> {
            val items = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            buildJsonArray {
                items.forEach { add(parseValue(it, type.items)) }
            }
        }
        is DataType.Object -> {
            // Very basic object parsing, assuming JSON if it looks like it
            try {
                Json.parseToJsonElement(value)
            } catch (e: Exception) {
                JsonPrimitive(value)
            }
        }
    }
}
