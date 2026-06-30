package org.wip.plugintoolkit.api

import kotlin.jvm.JvmInline

/**
 * A strongly-typed wrapper for relative file paths within a plugin sandbox.
 * 
 * Ensures that all paths used for file system operations are strictly relative
 * and cannot escape the designated root directory via path traversal attacks.
 */
@JvmInline
value class RelativePath private constructor(val value: String) {
    companion object {
        val ROOT = RelativePath("")
        private val NULL_BYTE_REGEX = Regex("\u0000")
        private val TRAVERSAL_REGEX = Regex("""(?:^|/|\\)\.\.(?:/|\\|$)""")
        
        /**
         * Safely converts a String to a RelativePath.
         * 
         * @return Result containing the RelativePath if valid, or a Failure if it contains
         * null bytes, absolute path indicators, or directory traversal segments (e.g. "../").
         */
        fun from(path: String): Result<RelativePath> {
            val normalized = path.trim()
            
            if (normalized.contains(NULL_BYTE_REGEX)) {
                return Result.failure(SecurityException("Path contains null bytes: $normalized"))
            }
            
            if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.startsWith("~")) {
                return Result.failure(SecurityException("Path must be relative, but starts with absolute indicator: $normalized"))
            }
            
            // Check for Windows drive letters (e.g., C:\)
            if (normalized.matches(Regex("""^[a-zA-Z]:[\\/].*"""))) {
                return Result.failure(SecurityException("Path must be relative, but contains drive letter: $normalized"))
            }
            
            // Note: %2e%2e%2f shouldn't normally reach here unescaped depending on the platform, 
            // but typical URL decodes would turn them into `../`, which is caught below.
            // If the host application uses URL-encoded strings directly on the file system, we catch literal traversal blocks:
            if (normalized.contains(TRAVERSAL_REGEX)) {
                return Result.failure(SecurityException("Path traversal attempt detected (contains '..'): $normalized"))
            }
            
            return Result.success(RelativePath(normalized))
        }
    }
}

/**
 * Extension to cleanly convert strings.
 */
fun String.toRelativePath(): Result<RelativePath> = RelativePath.from(this)
