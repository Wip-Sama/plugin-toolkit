package org.wip.plugintoolkit

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAppTest {

    @Test
    fun testVersionMatchesConfig() {
        assertTrue(AppConfig.VERSION.isNotEmpty())
    }

    @Test
    fun testDetectSystemConfig() {
        val config = detectSystemConfig()
        assertTrue(config.STARTUP_APP_NAME.isNotEmpty())
    }

}
