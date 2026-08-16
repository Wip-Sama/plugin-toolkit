package org.wip.plugintoolkit.core.model

interface LocalizedString {
    data class Raw(val text: String) : LocalizedString
    data class Resource(val key: String, val args: List<Any> = emptyList()) : LocalizedString
}


val String.localized: LocalizedString get() = LocalizedString.Raw(this)
