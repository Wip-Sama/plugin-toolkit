package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.ui.graphics.Color
import org.wip.plugintoolkit.core.utils.PlatformUtils
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowSnapTest {

    @Test
    fun testWindowFrameUtilsEnableUndecoratedDropShadow() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("DropShadowTestFrame")
        frame.isUndecorated = true
        frame.setSize(500, 400)
        frame.isVisible = true

        try {
            // Must execute cleanly without error or native border regression
            WindowFrameUtils.enableUndecoratedDropShadow(frame)
            assertTrue(frame.isUndecorated, "Window must remain undecorated")

            // Test native title bar theme helper as well
            WindowFrameUtils.setNativeTitleBarTheme(
                window = frame,
                isDark = true,
                captionColor = Color(0xFF1E1E1E),
                textColor = Color(0xFFFFFFFF)
            )
        } finally {
            frame.dispose()
        }
    }
}
