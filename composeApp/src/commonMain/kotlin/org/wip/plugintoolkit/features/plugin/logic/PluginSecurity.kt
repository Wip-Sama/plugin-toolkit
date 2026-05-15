package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.jar.JarFile

object PluginSecurity {

    /**
     * Verifies that the JAR file is signed by the provided public key.
     * Uses standard JVM JAR verification.
     * 
     * @param jarPath Path to the JAR file
     * @param publicKeyBase64 Base64 encoded X.509 public key
     * @return true if the JAR is correctly signed by the key, false otherwise
     */
    fun verify(jarPath: String, publicKeyBase64: String): Boolean {
        try {
            val publicKey = loadPublicKey(publicKeyBase64)
            return verifyJar(File(jarPath), publicKey)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to verify signature for $jarPath" }
            return false
        }
    }

    /**
     * Verifies a detached signature of a data string.
     */
    fun verifyDetached(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKey = loadPublicKey(publicKeyBase64)
            val sig = java.security.Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(data.toByteArray())
            sig.verify(Base64.getDecoder().decode(signatureBase64))
        } catch (e: Exception) {
            Logger.e(e) { "Failed to verify detached signature" }
            false
        }
    }

    private fun loadPublicKey(base64Str: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64Str)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }

    private fun verifyJar(file: File, trustedPublicKey: PublicKey): Boolean {
        // Open the JAR file with verification enabled
        val jar = JarFile(file, true)
        try {
            val entries = jar.entries()
            var hasSignedEntries = false

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                
                // Exclude the signature files themselves from needing to be signed
                if (entry.name.startsWith("META-INF/") && 
                    (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC"))) {
                    continue
                }

                // We must read the entry completely to trigger signature verification
                jar.getInputStream(entry).use { it.readBytes() }

                val signers = entry.codeSigners
                if (signers == null || signers.isEmpty()) {
                    // All non-signature files must be signed
                    if (entry.name != "META-INF/MANIFEST.MF") {
                        Logger.w { "Unsigned entry found in JAR: ${entry.name}" }
                        return false
                    }
                    continue
                }

                hasSignedEntries = true

                // Check if any of the signers matches our trusted public key
                val isTrusted = signers.any { signer ->
                    val certs = signer.signerCertPath.certificates
                    if (certs.isNotEmpty()) {
                        certs[0].publicKey == trustedPublicKey
                    } else {
                        false
                    }
                }

                if (!isTrusted) {
                    Logger.w { "Entry signed with untrusted key: ${entry.name}" }
                    return false
                }
            }
            
            if (!hasSignedEntries) {
                Logger.w { "JAR file contains no signed entries: ${file.name}" }
                return false
            }

            return true
        } finally {
            jar.close()
        }
    }
}
