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

private val semanticTypeRegex = Regex("""^(?:([^/]+)/)?([^:]+)(?::(.+))?$""")

fun parseSemanticType(token: String): SemanticType? {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) return null

    val match = semanticTypeRegex.matchEntire(trimmed) ?: return null
    val g1 = match.groupValues[1].takeIf { it.isNotEmpty() }
    val g2 = match.groupValues[2]
    val g3 = match.groupValues[3].takeIf { it.isNotEmpty() }

    if (g2.isBlank()) return null

    var namespace = g1
    var name = g2
    var variant = g3

    return SemanticType(namespace, name, variant)
}

fun parseSemanticTypes(value: String?): List<SemanticType> {
    if (value.isNullOrBlank()) return emptyList()
    return value.split(',', ' ', '\n', '\r', '\t')
        .filter { it.isNotBlank() }
        .mapNotNull { parseSemanticType(it) }
}
