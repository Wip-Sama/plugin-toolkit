package org.wip.plugintoolkit.core.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * A simple Markdown text component that supports:
 * **bold**, _italic_, ~strikethrough~, `monospace`, [link](url)
 * and escaping with \
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val uriHandler = LocalUriHandler.current
    val gray = org.wip.plugintoolkit.core.theme.ToolkitTheme.colors.gray
    val buttonBg = org.wip.plugintoolkit.core.theme.ToolkitTheme.opacity.buttonBackground
    val info = org.wip.plugintoolkit.core.theme.ToolkitTheme.colors.info
    val annotatedString = remember(text, gray, buttonBg, info) {
        parseMarkdown(text, gray, buttonBg, info)
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = if (color == Color.Unspecified) style.color else color),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}

private fun parseMarkdown(text: String, gray: Color, buttonBg: Float, info: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    append(text[i + 1])
                    i += 2
                }

                text.startsWith("**", i) -> {
                    val end = findClosing(text, i + 2, "**")
                    if (end != -1) {
                        val start = length
                        append(parseMarkdown(text.substring(i + 2, end), gray, buttonBg, info))
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }

                c == '_' -> {
                    val end = findClosing(text, i + 1, "_")
                    if (end != -1) {
                        val start = length
                        append(parseMarkdown(text.substring(i + 1, end), gray, buttonBg, info))
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                        i = end + 1
                    } else {
                        append("_")
                        i += 1
                    }
                }

                text.startsWith("~~", i) -> {
                    val end = findClosing(text, i + 2, "~~")
                    if (end != -1) {
                        val start = length
                        append(parseMarkdown(text.substring(i + 2, end), gray, buttonBg, info))
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                        i = end + 2
                    } else {
                        append("~~")
                        i += 2
                    }
                }

                c == '`' -> {
                    val end = findClosing(text, i + 1, "`")
                    if (end != -1) {
                        val start = length
                        append(text.substring(i + 1, end))
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = gray.copy(alpha = buttonBg)
                            ), start, length
                        )
                        i = end + 1
                    } else {
                        append("`")
                        i += 1
                    }
                }

                c == '[' -> {
                    val linkEnd = findClosing(text, i + 1, "]")
                    if (linkEnd != -1 && linkEnd + 1 < text.length && text[linkEnd + 1] == '(') {
                        val urlEnd = findClosing(text, linkEnd + 2, ")")
                        if (urlEnd != -1) {
                            val linkText = text.substring(i + 1, linkEnd)
                            val url = text.substring(linkEnd + 2, urlEnd)
                            val start = length
                            append(parseMarkdown(linkText, gray, buttonBg, info))
                            addStyle(
                                SpanStyle(
                                    color = info,
                                    textDecoration = TextDecoration.Underline
                                ), start, length
                            )
                            addStringAnnotation("URL", url, start, length)
                            i = urlEnd + 1
                        } else {
                            append("[")
                            i += 1
                        }
                    } else {
                        append("[")
                        i += 1
                    }
                }

                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
}

private fun findClosing(text: String, start: Int, delimiter: String): Int {
    var i = start
    while (i < text.length) {
        if (text.startsWith("\\", i)) {
            i += 2
            continue
        }
        if (text.startsWith(delimiter, i)) {
            return i
        }
        i++
    }
    return -1
}
