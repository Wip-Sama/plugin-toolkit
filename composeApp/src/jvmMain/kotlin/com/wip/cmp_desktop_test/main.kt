package com.wip.cmp_desktop_test

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WOToolkit",
    ) {
        App()
    }
}