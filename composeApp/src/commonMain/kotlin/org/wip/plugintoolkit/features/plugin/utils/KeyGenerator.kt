package org.wip.plugintoolkit.features.plugin.utils

expect object KeyGenerator {
    fun generateKeyPair(): Pair<String, String>
}
