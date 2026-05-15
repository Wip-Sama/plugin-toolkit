package org.wip.plugintoolkit.features.plugin.utils

import java.security.KeyPairGenerator
import java.util.Base64

/**
 * Entry point to run the key generator from the IDE.
 */
fun main() {
    val (priv, pub) = KeyGenerator.generateKeyPair()
    println("Generated Plugin Signing Keys:")
    println("-----------------------------")
    println("PRIVATE KEY (Keep secret!):")
    println(priv)
    println("\nPUBLIC KEY (Publish in repo index):")
    println(pub)
}

/**
 * Utility for developers to generate RSA key pairs for plugin signing.
 */
object KeyGenerator {
    /**
     * Generates a new RSA 2048-bit key pair.
     * @return Pair of (PrivateKey Base64, PublicKey Base64)
     */
    fun generateKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        
        val privateKey = Base64.getEncoder().encodeToString(kp.private.encoded)
        val publicKey = Base64.getEncoder().encodeToString(kp.public.encoded)
        
        return privateKey to publicKey
    }
}
