package org.wip.plugintoolkit.ui.splash

import java.awt.GraphicsEnvironment
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SplashScreenTest {

    @Test
    fun testSplashWindowLifecycle() {
        if (GraphicsEnvironment.isHeadless()) {
            println("Skipping testSplashWindowLifecycle in headless environment")
            return
        }

        var splash: SplashWindow? = null
        SwingUtilities.invokeAndWait {
            val window = JWindow()
            splash = SplashWindow(window)
        }

        val instance = splash
        assertNotNull(instance, "SplashWindow should be created")

        // Update text should work without errors from background or EDT
        instance.updateText("Initializing test modules...")
        instance.updateText("Loading components...")

        // Dispose should cleanly stop the animation timer and dispose the underlying window
        instance.dispose()

        // Allow EDT events to drain
        SwingUtilities.invokeAndWait {
            assertFalse(instance.window.isVisible, "Splash window should not be visible after dispose")
        }
    }

    @Test
    fun testShowSplashWindow() {
        if (GraphicsEnvironment.isHeadless()) {
            println("Skipping testShowSplashWindow in headless environment")
            return
        }

        var splash: SplashWindow? = null
        SwingUtilities.invokeAndWait {
            splash = showSplashWindow()
        }

        val instance = splash
        assertNotNull(instance, "showSplashWindow should return non-null instance")
        assertTrue(instance.window.isVisible, "Splash window should be visible after showSplashWindow")

        instance.updateText("Ready")
        instance.dispose()

        SwingUtilities.invokeAndWait {
            assertFalse(instance.window.isVisible, "Splash window should not be visible after dispose")
        }
    }
}
