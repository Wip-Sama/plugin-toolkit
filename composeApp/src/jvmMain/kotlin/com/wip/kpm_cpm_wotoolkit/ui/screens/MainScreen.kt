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
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.compose_multiplatform
import org.koin.mp.KoinPlatformTools

@Composable
fun MainScreen() {
    var showContent by remember { mutableStateOf(false) }
    var jarPath by remember { mutableStateOf("additions/build/libs/additions.jar") }
    var moduleStatus by remember { mutableStateOf("Not Loaded") }
    var calculationResult by remember { mutableStateOf("") }
    var parameters by remember { mutableStateOf("10 20") }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Koin Dynamic Module Loading", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = jarPath,
            onValueChange = { jarPath = it },
            label = { Text("External JAR Path") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val result = DynamicModuleLoader.loadModule(
                    jarPath = jarPath,
                    moduleClassName = "com.wip.additions.SumServiceKt",
                    modulePropertyName = "additionsModule"
                )
                moduleStatus = if (result.isSuccess) "Loaded Successfully" else "Load Failed: ${result.exceptionOrNull()?.message}"
            }) {
                Text("Load Module")
            }

            Button(onClick = {
                val result = DynamicModuleLoader.unloadModule()
                moduleStatus = if (result.isSuccess) "Unloaded Successfully" else "Unload Failed"
            }) {
                Text("Unload Module")
            }
        }

        Text("Status: $moduleStatus", style = MaterialTheme.typography.bodyMedium)
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        TextField(
            value = parameters,
            onValueChange = { parameters = it },
            label = { Text("Enter numbers (space separated)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                try {
                    val classLoader = DynamicModuleLoader.getClassLoader()
                    if (classLoader == null) {
                        calculationResult = "Error: Module not loaded (no classloader)"
                        return@Button
                    }

                    val koin = KoinPlatformTools.defaultContext().get()
                    val serviceClass = classLoader.loadClass("com.wip.additions.SumService")
                    val serviceInstance = koin.get<Any>(clazz = serviceClass.kotlin)
                    
                    val method = serviceClass.getMethod("calculateSum", Array<String>::class.java)
                    val argsArray = parameters.split(" ").toTypedArray()
                    val result = method.invoke(serviceInstance, argsArray) as String
                    calculationResult = "Result: $result"
                } catch (e: Exception) {
                    calculationResult = "Error: ${e.message}"
                    e.printStackTrace()
                }
            }
        ) {
            Text("Calculate Sum (via Reflection)")
        }

        Text(calculationResult, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { showContent = !showContent }) {
            Text(if (showContent) "Hide Default Content" else "Show Default Content")
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
