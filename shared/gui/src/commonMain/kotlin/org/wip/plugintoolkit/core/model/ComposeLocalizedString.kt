package org.wip.plugintoolkit.core.model

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.ui.LocalLanguage

data class ComposeLocalizedString(val res: StringResource, val args: List<Any> = emptyList()) : LocalizedString

@Composable
fun LocalizedString.resolve(): String = when (this) {
    is LocalizedString.Raw -> text
    is LocalizedString.Resource -> key
    is ComposeLocalizedString -> {
        LocalLanguage.current
        if (args.isEmpty()) stringResource(res) else stringResource(res, *args.toTypedArray())
    }
    else -> toString()
}

fun LocalizedString.resolveNonComposable(vararg additionalArgs: Any): String = when (this) {
    is LocalizedString.Raw -> text
    is LocalizedString.Resource -> key
    is ComposeLocalizedString -> kotlinx.coroutines.runBlocking {
        org.jetbrains.compose.resources.getString(res, *(args + additionalArgs.toList()).toTypedArray())
    }
    else -> toString()
}

val StringResource.localized: LocalizedString get() = ComposeLocalizedString(this)
fun StringResource.localizedWithArgs(vararg args: Any): LocalizedString = ComposeLocalizedString(this, args.toList())
