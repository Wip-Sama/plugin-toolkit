package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings

open class JobWorkerFlowTestBase {
    protected val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @kotlin.test.AfterTest
    open fun tearDownTest() {
        try {
            org.koin.core.context.stopKoin()
        } catch (e: Exception) {}
        io.mockk.unmockkAll()
    }

    protected class FakeSettingsPersistence : SettingsPersistence {
        private val uniqueId = kotlin.random.Random.nextInt().toString()
        init {
            try {
                kotlinx.io.files.SystemFileSystem.createDirectories(kotlinx.io.files.Path(getSettingsDir()))
                kotlinx.io.files.SystemFileSystem.createDirectories(kotlinx.io.files.Path(getJobsDir()))
            } catch (e: Exception) {}
        }
        var settings = AppSettings(
            jobs = org.wip.plugintoolkit.features.settings.model.JobSettings(pluginTimeoutMs = -1L)
        )
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) {
            this.settings = settings
        }

        override fun getSettingsDir(): String = "build/tmp/test_flows_$uniqueId"
        override fun getJobsDir(): String = "build/tmp/test_flows_$uniqueId/jobs"
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
