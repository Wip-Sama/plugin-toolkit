package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.JobWorker
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

open class JobWorkerFlowTestBase {
    protected val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }


    protected class FakeSettingsPersistence : SettingsPersistence {
            var settings = AppSettings()
            override fun load(): AppSettings = settings
            override fun save(settings: AppSettings) {
                this.settings = settings
            }
    
            override fun getSettingsDir(): String = "/tmp"
            override fun getJobsDir(): String = "/tmp/jobs"
            override fun openLogFolder() {}
            override fun openLatestLog() {}
        }
    
        protected fun createInputNode(
            id: Long,
            name: String,
            dataType: DataType,
            semanticTypes: List<SemanticType> = emptyList()
        ): Node.FlowInputNode {
            return Node.FlowInputNode(
                id = id,
                position = Offset.Zero,
                outputs = listOf(
                    OutputPort(id = "output_data", name = name, dataType = dataType, semanticTypes = semanticTypes)
                )
            )
        }
    
        protected fun createSystemNode(id: Long, action: String): Node.SystemNode {
            return Node.SystemNode(
                id = id,
                position = Offset.Zero,
                title = action.replaceFirstChar { it.uppercase() },
                systemAction = action,
                inputs = SystemNodesRegistry.getInputs(action),
                outputs = SystemNodesRegistry.getOutputs(action)
            )
        }
    
        protected fun createOutputNode(id: Long, name: String, dataType: DataType): Node.FlowOutputNode {
            return Node.FlowOutputNode(
                id = id,
                position = Offset.Zero,
                inputs = listOf(
                    InputPort(id = "input_data", name = name, dataType = dataType)
                )
            )
        }
}
