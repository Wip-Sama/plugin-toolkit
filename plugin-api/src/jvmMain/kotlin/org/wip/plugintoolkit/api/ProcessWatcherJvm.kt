package org.wip.plugintoolkit.api

/**
 * Watch a [java.lang.Process] spawned by this plugin.
 *
 * @param process The running external process.
 * @return A [ProcessWatcher] handle.
 */
fun PluginContext.watchProcess(process: java.lang.Process): ProcessWatcher {
    return watchProcess(process.toHandle().pid())
}
