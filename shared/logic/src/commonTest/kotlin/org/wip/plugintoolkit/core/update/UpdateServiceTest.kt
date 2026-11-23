package org.wip.plugintoolkit.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateServiceTest {

    private fun createService(): UpdateService {
        val mockEngine = MockEngine { respondOk("{}") }
        return UpdateService(client = HttpClient(mockEngine), appConfig = null)
    }

    @Test
    fun `isNewer handles stable and alpha pre-release versions`() {
        val service = createService()

        assertTrue(service.isNewer("4.1.0-alpha01", "4.0.0"))
        assertTrue(service.isNewer("4.1.0-alpha02", "4.1.0-alpha01"))
        assertTrue(service.isNewer("4.1.0-beta01", "4.1.0-alpha05"))
        assertTrue(service.isNewer("4.1.0", "4.1.0-alpha01"))

        assertFalse(service.isNewer("4.1.0-alpha01", "4.1.0-alpha01"))
        assertFalse(service.isNewer("4.0.0", "4.1.0-alpha01"))
        assertFalse(service.isNewer("4.1.0-alpha01", "4.1.0"))
    }

    @Test
    fun `extractVersion extracts stable and pre-release suffixes without installer labels`() {
        val service = createService()

        assertEquals("4.1.0-alpha01", service.extractVersion("PluginToolkit-4.1.0-alpha01-setup.exe"))
        assertEquals("4.1.0-beta2", service.extractVersion("PluginToolkit-4.1.0-beta2.msi"))
        assertEquals("4.1.0-rc.1", service.extractVersion("PluginToolkit-4.1.0-rc.1.AppImage"))
        assertEquals("4.0.0", service.extractVersion("PluginToolkit-4.0.0-windows-x64.exe"))
        assertNull(service.extractVersion("PluginToolkit-installer.exe"))
    }
}
