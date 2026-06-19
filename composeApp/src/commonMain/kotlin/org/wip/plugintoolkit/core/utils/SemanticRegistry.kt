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

object SemanticRegistry {
    /**
     * Maps a list of SemanticTypes to a SemanticCategory choosing the "best" match based on priority:
     * COLOR > IMAGE > AUDIO > VIDEO > FILE > PATH.
     */
    fun getCategory(types: List<SemanticType>): SemanticCategory? {
        val mappedCategories = types.mapNotNull { type ->
            when (type.name.lowercase()) {
                "color" -> SemanticCategory.COLOR
                "image" -> SemanticCategory.IMAGE
                "audio" -> SemanticCategory.AUDIO
                "video" -> SemanticCategory.VIDEO
                "file" -> SemanticCategory.FILE
                "path" -> SemanticCategory.PATH
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
    fun getAllowedExtensions(types: List<SemanticType>): List<String> {
        val extensions = mutableListOf<String>()
        var hasGenericImage = false
        var hasGenericAudio = false
        var hasGenericVideo = false

        for (type in types) {
            val nameLower = type.name.lowercase()
            val variant = type.variant

            when (nameLower) {
                "image" -> {
                    if (variant == null || variant == "*") {
                        hasGenericImage = true
                    } else {
                        extensions.add(variant)
                    }
                }

                "audio" -> {
                    if (variant == null || variant == "*") {
                        hasGenericAudio = true
                    } else {
                        extensions.add(variant)
                    }
                }

                "video" -> {
                    if (variant == null || variant == "*") {
                        hasGenericVideo = true
                    } else {
                        extensions.add(variant)
                    }
                }

                "file" -> {
                    if (variant != null && variant != "*") {
                        extensions.add(variant)
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
