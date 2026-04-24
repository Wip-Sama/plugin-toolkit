package com.wip.kpm_cpm_wotoolkit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        // You can add default modules here if needed
        modules(emptyList())
    }
    
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WOToolkit",
        ) {
            App()
        }
    }
}
