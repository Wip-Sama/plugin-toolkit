package org.wip.plugintoolkit.features.plugin.utils

import java.security.KeyPairGenerator
import java.util.Base64

actual object KeyGenerator {
    actual fun generateKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val privateKey = Base64.getEncoder().encodeToString(kp.private.encoded)
        val publicKey = Base64.getEncoder().encodeToString(kp.public.encoded)

        return privateKey to publicKey
    }
}
