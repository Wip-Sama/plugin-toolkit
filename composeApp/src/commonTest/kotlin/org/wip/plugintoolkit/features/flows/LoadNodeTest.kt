package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.logic.LoadNodeExecutor
import org.wip.plugintoolkit.features.job.logic.NodeExecutionContext
import org.wip.plugintoolkit.features.job.logic.SaveNodeExecutor
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadNodeTest {

    private class MockNodeExecutionContext(
        override val node: Node.SystemNode,
        override val appDataDir: String,
        private val inputs: Map<String, Any?>,
    ) : NodeExecutionContext {
        override val job: BackgroundJob = BackgroundJob(
            id = "test",
            name = "test",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "test"
        )
        override val resumeState: kotlinx.serialization.json.JsonElement? = null
        override val runtimeInferredTypes: Map<Pair<Long, String>, DataType> = emptyMap()
        val outputs = mutableMapOf<String, Any?>()
        val logs = mutableListOf<String>()

        override fun getInputValue(portId: String, defaultValue: Any?): Any? = inputs[portId] ?: defaultValue
        override fun setOutputValue(portId: String, value: Any?) {
            outputs[portId] = value
        }

        override fun addLog(message: String, level: String) {
            logs.add(message)
        }

        override suspend fun executeSubFlow(
            flowName: String,
            parameters: Map<String, kotlinx.serialization.json.JsonElement>
        ): Map<String, Any?> = emptyMap()
    }

    @Test
    fun testLoadSaveRelativePath() = runTest {
        val tempDir = "build/tmp/load_node_test_rel"
        SystemFileSystem.createDirectories(Path(tempDir))

        try {
            val fileName = "test_relative.txt"
            val content = "Hello Relative Path"

            // Save
            val saveNode = Node.SystemNode(1, Offset.Zero, "Save", "save", emptyList(), emptyList())
            val saveContext =
                MockNodeExecutionContext(saveNode, tempDir, mapOf("data" to content, "file_path" to fileName))
            SaveNodeExecutor().execute(saveContext)

            val expectedPath = Path(tempDir, fileName)
            assertTrue(SystemFileSystem.exists(expectedPath), "File should exist at $expectedPath")

            // Load
            val loadNode = Node.SystemNode(2, Offset.Zero, "Load", "load", emptyList(), emptyList())
            val loadContext = MockNodeExecutionContext(loadNode, tempDir, mapOf("file_path" to fileName))
            LoadNodeExecutor().execute(loadContext)

            assertEquals(content, loadContext.outputs["data"])
        } finally {
            deleteRecursively(Path(tempDir))
        }
    }

    @Test
    fun testLoadSaveAbsolutePath() = runTest {
        // Use a path that is likely to be absolute and writable in the test environment
        val baseDirName = "load_node_test_abs"
        val currentDir = SystemFileSystem.resolve(Path("."))
        val absoluteBase = Path(currentDir, "build/tmp/$baseDirName")

        // Ensure directory exists BEFORE testing absolute paths
        SystemFileSystem.createDirectories(absoluteBase)

        try {
            val fileName = "test_absolute.txt"
            val absoluteFilePath = Path(absoluteBase, fileName).toString()
            val content = "Hello Absolute Path"

            // Save
            val saveNode = Node.SystemNode(1, Offset.Zero, "Save", "save", emptyList(), emptyList())
            // Pass absolute path as file_path. We use a dummy appDataDir.
            val saveContext =
                MockNodeExecutionContext(saveNode, "dummy", mapOf("data" to content, "file_path" to absoluteFilePath))
            SaveNodeExecutor().execute(saveContext)

            val savedPath = Path(absoluteFilePath)
            assertTrue(SystemFileSystem.exists(savedPath), "File should exist at absolute path: $absoluteFilePath")

            // Load
            val loadNode = Node.SystemNode(2, Offset.Zero, "Load", "load", emptyList(), emptyList())
            // Pass absolute path as file_path
            val loadContext = MockNodeExecutionContext(loadNode, "dummy", mapOf("file_path" to absoluteFilePath))
            LoadNodeExecutor().execute(loadContext)

            assertEquals(content, loadContext.outputs["data"], "Content should match for absolute path")
        } finally {
            deleteRecursively(absoluteBase)
        }
    }

    private fun deleteRecursively(path: Path) {
        if (SystemFileSystem.exists(path)) {
            val metadata = SystemFileSystem.metadataOrNull(path)
            if (metadata?.isDirectory == true) {
                SystemFileSystem.list(path).forEach { child ->
                    deleteRecursively(child)
                }
            }
            SystemFileSystem.delete(path)
        }
    }
}
