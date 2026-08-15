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

            if (normalized.contains(ENCODED_SLASH_REGEX)) {
                return Result.failure(SecurityException("Path traversal attempt detected: $normalized"))
            }

            val segments = normalized.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
            val validationSegments = segments.map { segment ->
                segment
                    .replace("\u2024", ".")
                    .replace("\uFF0E", ".")
                    .replace("\u3002", ".")
                    .replace("%2e", ".", ignoreCase = true)
            }
            if (validationSegments.any {
                    it == ".." || it.contains(Regex("%25(?:2e|2f|5c)", RegexOption.IGNORE_CASE))
                }
            ) {
                return Result.failure(SecurityException("Path traversal attempt detected: $normalized"))
            }

            // Validation uses a security-normalized view, but the filename itself is not decoded or rewritten.
            return Result.success(RelativePath(segments.joinToString("/")))
        }
    }
}

/**
 * Extension to cleanly convert strings.
 */
fun String.toRelativePath(): Result<RelativePath> = RelativePath.from(this)
