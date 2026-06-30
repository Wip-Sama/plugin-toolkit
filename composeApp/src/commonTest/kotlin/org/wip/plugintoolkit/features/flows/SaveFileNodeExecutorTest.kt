package org.wip.plugintoolkit.features.flows

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.features.job.logic.DefaultSystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.job.logic.NodeExecutionContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveFileNodeExecutorTest : JobWorkerFlowTestBase() {
    
    @BeforeTest
    fun setup() {
    }

    @AfterTest
    fun tearDown() {
    }

    @Test
    fun testSaveFileNodeExecutor() = runTest {
        val registry = DefaultSystemNodeExecutorRegistry(mockk(relaxed = true))
        val executor = registry.getExecutor("save_file")
        
        val persistence = FakeSettingsPersistence()
        val appDataDir = persistence.getSettingsDir()

        val sourceFile = Path(appDataDir, "source.txt")
        val destFolder = Path(appDataDir, "dest")
        
        SystemFileSystem.sink(sourceFile).buffered().use { it.writeString("hello") }
        SystemFileSystem.createDirectories(destFolder)
        
        val context = mockk<NodeExecutionContext>(relaxed = true)
        every { context.appDataDir } returns appDataDir
        every { context.getInputValue("data", any()) } returns JsonPrimitive(sourceFile.toString())
        every { context.getInputValue("destination_folder", any()) } returns JsonPrimitive(destFolder.toString())
        every { context.getInputValue("file_name", any()) } returns ""
        every { context.getInputValue("is_destructive", any()) } returns false

        executor.execute(context)
        
        val destFile = Path(destFolder, "source.txt")
        assertTrue(SystemFileSystem.exists(destFile))
        assertTrue(SystemFileSystem.exists(sourceFile)) // not destructive
        assertEquals("hello", SystemFileSystem.source(destFile).buffered().use { it.readString() })
        
        verify { context.setOutputValue("success", true) }
        verify { context.setOutputValue("saved_path", destFile.toString()) }
    }
}
