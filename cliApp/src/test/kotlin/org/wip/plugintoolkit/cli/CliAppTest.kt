package org.wip.plugintoolkit.cli

import kotlin.test.Test
import kotlin.test.assertTrue

class CliAppTest {

    @Test
    fun testVersionMatchesConfig() {
        assertTrue(CliAppConfig.VERSION.isNotEmpty())
    }

    @Test
    fun testCliCommandInstantiation() {
        val cli = PluginToolkitCli()
        assertTrue(cli.commandName == "plugin-toolkit")
    }
}
