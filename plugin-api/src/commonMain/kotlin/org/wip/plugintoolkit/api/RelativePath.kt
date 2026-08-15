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
        private val TRAVERSAL_REGEX = Regex("""(?:^|/|\\|\.|\u2024|\uFF0E|\u3002)(?:\.\.|\u2024\u2024|\uFF0E\uFF0E|%2e%2e|%2E%2E|%252e%252e)(?:/|\\|${'$'}|\.)""", RegexOption.IGNORE_CASE)
        private val ENCODED_SLASH_REGEX = Regex("""%2f|%5c""", RegexOption.IGNORE_CASE)

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

            // Check for encoded slashes or traversal sequences
            if (normalized.contains(ENCODED_SLASH_REGEX) || normalized.contains(TRAVERSAL_REGEX)) {
                return Result.failure(SecurityException("Path traversal attempt detected: $normalized"))
            }

            // Normalized path checks: convert unicode dot variants to regular dot for safety check
            val sanitized = normalized
                .replace("\u2024", ".")
                .replace("\uFF0E", ".")
                .replace("\u3002", ".")
                .replace("%2e", ".", ignoreCase = true)
                .replace("%2f", "/", ignoreCase = true)
                .replace("%5c", "\\", ignoreCase = true)

            if (sanitized.contains(TRAVERSAL_REGEX) || sanitized.contains("../") || sanitized.contains("..\\")) {
                return Result.failure(SecurityException("Path traversal attempt detected: $normalized"))
            }
            
            return Result.success(RelativePath(normalized))
        }
    }
}

/**
 * Extension to cleanly convert strings.
 */
fun String.toRelativePath(): Result<RelativePath> = RelativePath.from(this)
