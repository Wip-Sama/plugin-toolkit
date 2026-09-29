package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.api.SemanticType

enum class SemanticCategory {
    COLOR,
    IMAGE,
    AUDIO,
    VIDEO,
    FILE,
    PATH
}

interface SemanticRegistry {
    /**
     * Maps a list of SemanticTypes to a SemanticCategory choosing the "best" match based on priority:
     * COLOR > IMAGE > AUDIO > VIDEO > FILE > PATH.
     */
    fun getCategory(types: List<SemanticType>): SemanticCategory?

    /**
     * Extracts allowed file extensions from a list of SemanticTypes.
     */
    fun getAllowedExtensions(types: List<SemanticType>): List<String>
}

class DefaultSemanticRegistry : SemanticRegistry {
    /**
     * Maps a list of SemanticTypes to a SemanticCategory choosing the "best" match based on priority:
     * COLOR > IMAGE > AUDIO > VIDEO > FILE > PATH.
     */
    override fun getCategory(types: List<SemanticType>): SemanticCategory? {
        val mappedCategories = types.mapNotNull { type ->
            val ns = type.namespace?.lowercase()
            val name = type.name.lowercase()
            val primary = (type.namespace ?: type.name).lowercase()
            when {
                ns == "path" && name == "file" -> SemanticCategory.FILE
                ns == "path" && name == "folder" -> SemanticCategory.PATH
                name == "folder" -> SemanticCategory.PATH
                primary == "color" -> SemanticCategory.COLOR
                primary == "image" -> SemanticCategory.IMAGE
                primary == "audio" -> SemanticCategory.AUDIO
                primary == "video" -> SemanticCategory.VIDEO
                primary == "file" -> SemanticCategory.FILE
                primary == "path" -> SemanticCategory.PATH
                else -> null
            }
        }.toSet()

        return when {
            SemanticCategory.COLOR in mappedCategories -> SemanticCategory.COLOR
            SemanticCategory.IMAGE in mappedCategories -> SemanticCategory.IMAGE
            SemanticCategory.AUDIO in mappedCategories -> SemanticCategory.AUDIO
            SemanticCategory.VIDEO in mappedCategories -> SemanticCategory.VIDEO
            SemanticCategory.FILE in mappedCategories -> SemanticCategory.FILE
            SemanticCategory.PATH in mappedCategories -> SemanticCategory.PATH
            else -> null
        }
    }

    /**
     * Extracts allowed file extensions from a list of SemanticTypes.
     */
    override fun getAllowedExtensions(types: List<SemanticType>): List<String> {
        val extensions = mutableListOf<String>()
        var hasGenericImage = false
        var hasGenericAudio = false
        var hasGenericVideo = false

        for (type in types) {
            val ns = type.namespace?.lowercase()
            val name = type.name.lowercase()
            val primary = (type.namespace ?: type.name).lowercase()

            if (ns == "path" && name == "file") {
                val ext = type.variant
                if (ext != null && ext != "*") {
                    extensions.add(ext)
                }
            }

            when (primary) {
                "image" -> {
                    val ext = if (type.namespace != null) type.name else type.variant
                    if (ext == null || ext == "*") {
                        hasGenericImage = true
                    } else {
                        extensions.add(ext)
                    }
                }

                "audio" -> {
                    val ext = if (type.namespace != null) type.name else type.variant
                    if (ext == null || ext == "*") {
                        hasGenericAudio = true
                    } else {
                        extensions.add(ext)
                    }
                }

                "video" -> {
                    val ext = if (type.namespace != null) type.name else type.variant
                    if (ext == null || ext == "*") {
                        hasGenericVideo = true
                    } else {
                        extensions.add(ext)
                    }
                }

                "file" -> {
                    val ext = if (type.namespace != null) type.name else type.variant
                    if (ext != null && ext != "*") {
                        extensions.add(ext)
                    }
                }
            }
        }

        if (hasGenericImage) {
            extensions.addAll(listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"))
        }
        if (hasGenericAudio) {
            extensions.addAll(listOf("mp3", "wav", "ogg", "aac", "flac", "m4a"))
        }
        if (hasGenericVideo) {
            extensions.addAll(listOf("mp4", "mkv", "avi", "mov", "webm", "flv"))
        }

        return extensions.distinct()
    }
}
