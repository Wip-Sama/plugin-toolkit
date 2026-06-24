package org.wip.plugintoolkit.api

/**
 * A registry of well-known, standard [SemanticType]s.
 *
 * Plugins should rely on a common base of semantic types to ensure compatibility
 * and consistent behavior. Use these predefined types when applicable instead of
 * inventing custom variations.
 *
 * ## Guidance on Semantic Types
 *
 * Semantic types describe the *semantic meaning* or the structural format of data,
 * not its purpose in an arbitrary workflow.
 *
 * **Best Practices**:
 * - Use [PATH] for file system paths.
 * - Use [PATH_FILE] for paths specifically pointing to files.
 * - Use [PATH_FOLDER] for paths specifically pointing to directories.
 * - Use [IMAGE_PNG] for PNG images.
 *
 * **Anti-Patterns**:
 * - Do NOT use types like `file/input` or `file/output`. These describe the data's
 *   direction or role in a specific process, not what the data actually is. Use
 *   metadata, field names, or parameter configuration to define inputs vs outputs.
 * - Do NOT use mixed abstraction types like `path/png`. If a variable holds a string path
 *   to a PNG, the semantic type of the *variable* is `path/file`. The fact that it points
 *   to a PNG can be expressed via additional constraints or formats, but the primary type
 *   is a path.
 */
object CommonSemanticTypes {

    // --- Path types ---

    const val PATH_ID = "path"
    /** Represents a generic filesystem path. (`path`) */
    val PATH = SemanticType(namespace = null, name = "path", variant = null)

    const val PATH_FILE_ID = "path/file"
    /** A path that strictly points to a file. (`path/file`) */
    val PATH_FILE = SemanticType(namespace = "path", name = "file", variant = null)

    const val PATH_FOLDER_ID = "path/folder"
    /** A path that strictly points to a folder/directory. (`path/folder`) */
    val PATH_FOLDER = SemanticType(namespace = "path", name = "folder", variant = null)

    // --- Image types ---

    const val IMAGE_ID = "image"
    /** A generic image type. (`image`) */
    val IMAGE = SemanticType(namespace = null, name = "image", variant = null)

    const val IMAGE_PNG_ID = "image/png"
    /** A PNG image. (`image/png`) */
    val IMAGE_PNG = SemanticType(namespace = "image", name = "png", variant = null)

    const val IMAGE_JPEG_ID = "image/jpeg"
    /** A JPEG image. (`image/jpeg`) */
    val IMAGE_JPEG = SemanticType(namespace = "image", name = "jpeg", variant = null)

    // --- Text types ---

    const val TEXT_ID = "text"
    /** Generic text content. (`text`) */
    val TEXT = SemanticType(namespace = null, name = "text", variant = null)

    const val TEXT_PLAIN_ID = "text/plain"
    /** Plain text. (`text/plain`) */
    val TEXT_PLAIN = SemanticType(namespace = "text", name = "plain", variant = null)

    const val TEXT_MARKDOWN_ID = "text/markdown"
    /** Markdown text. (`text/markdown`) */
    val TEXT_MARKDOWN = SemanticType(namespace = "text", name = "markdown", variant = null)

    const val TEXT_HTML_ID = "text/html"
    /** HTML content. (`text/html`) */
    val TEXT_HTML = SemanticType(namespace = "text", name = "html", variant = null)

    // --- Data types ---

    const val JSON_ID = "json"
    /** JSON data. (`json`) */
    val JSON = SemanticType(namespace = null, name = "json", variant = null)

    const val XML_ID = "xml"
    /** XML data. (`xml`) */
    val XML = SemanticType(namespace = null, name = "xml", variant = null)
}
