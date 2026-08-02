package org.wip.plugintoolkit.features.plugin.logic

expect object PluginSecurity {
    fun verify(jarPath: String, publicKeyBase64: String): Boolean
    fun verifyDetached(data: String, signatureBase64: String, publicKeyBase64: String): Boolean
}
