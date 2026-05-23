package org.wip.plugintoolkit.api

import kotlinx.serialization.Serializable

expect fun normalizeNFKC(str: String): String

@Serializable
class SemanticType {
    val namespace: String?
    val name: String
    val variant: String?

    constructor(namespace: String?, name: String, variant: String?) {
        this.namespace = namespace?.trim()?.lowercase()?.let { normalizeNFKC(it) }?.takeIf { it.isNotEmpty() }
        this.name = normalizeNFKC(name.trim().lowercase())
        this.variant = variant?.trim()?.lowercase()?.let { normalizeNFKC(it) }?.takeIf { it.isNotEmpty() }
    }

    val canonicalId: String get() = buildString {
        if (namespace != null) append("$namespace/")
        append(name)
        if (variant != null) append(":$variant")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticType) return false
        return namespace == other.namespace && name == other.name && variant == other.variant
    }

    override fun hashCode(): Int {
        var result = namespace?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + (variant?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = canonicalId
}

fun parseSemanticType(token: String): SemanticType? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null

    val colonIndex = trimmed.indexOf(':')
    val hasColon = colonIndex != -1
    val left = if (hasColon) trimmed.substring(0, colonIndex) else trimmed
    val variantPart = if (hasColon) trimmed.substring(colonIndex + 1) else null

    val slashIndex = left.indexOf('/')
    val hasSlash = slashIndex != -1

    val namespace: String?
    val name: String
    val variant: String?

    if (hasSlash) {
        val part1 = left.substring(0, slashIndex)
        val part2 = left.substring(slashIndex + 1)

        if (hasColon) {
            namespace = part1
            name = part2
            variant = variantPart
        } else {
            val isMimeCategory = part1.lowercase() in setOf(
                "image", "audio", "video", "text", "application", "font", "file", "color", "path"
            )
            if (isMimeCategory) {
                namespace = null
                name = part1
                variant = part2
            } else {
                namespace = part1
                name = part2
                variant = null
            }
        }
    } else {
        namespace = null
        name = left
        variant = variantPart
    }

    if (name.isBlank()) return null

    return SemanticType(namespace, name, variant)
}

fun parseSemanticTypes(value: String?): List<SemanticType> {
    if (value.isNullOrBlank()) return emptyList()
    return value.split(Regex("[\\s,]+"))
        .filter { it.isNotBlank() }
        .mapNotNull { parseSemanticType(it) }
}
