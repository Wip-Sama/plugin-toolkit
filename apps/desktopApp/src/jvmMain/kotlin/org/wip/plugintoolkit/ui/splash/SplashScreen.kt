package org.wip.plugintoolkit.ui.splash

import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.swing.ImageIcon
import javax.swing.JWindow
import javax.swing.SwingUtilities

private fun renderSplashFrame(
    g2: Graphics2D,
    text: String,
    logo: Image?
) {
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

    // Draw background
    g2.color = java.awt.Color(30, 30, 30)
    g2.fillRoundRect(0, 0, 400, 300, 32, 32)

    // Draw logo
    if (logo != null) {
        g2.drawImage(logo, 152, 40, 96, 96, null)
    }

    // Draw Spinner
    val time = System.nanoTime() / 1_000_000.0
    val cycleTime = 1333.0
    val cycles = time / cycleTime
    val baseRotation = (time / 2000.0) * -360.0
    val cycleOffset = Math.floor(cycles) * -260.0
    val cycleFraction = cycles - Math.floor(cycles)

    val p = if (cycleFraction < 0.5) cycleFraction * 2.0 else (cycleFraction - 0.5) * 2.0
    val eased = if (p < 0.5) 4 * p * p * p else 1 - Math.pow(-2 * p + 2, 3.0) / 2

    val start: Double
    val extent: Double

    if (cycleFraction < 0.5) {
        extent = 10.0 + eased * 260.0
        start = baseRotation + cycleOffset
    } else {
        extent = 270.0 - eased * 260.0
        start = baseRotation + cycleOffset - (eased * 260.0)
    }

    g2.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g2.color = java.awt.Color(100, 181, 246)
    val arc = Arc2D.Double(168.0, 168.0, 64.0, 64.0, start, -extent, Arc2D.OPEN)
    g2.draw(arc)

    // Draw text
    g2.color = java.awt.Color.WHITE
    if (g2.font != null) {
        g2.font = g2.font.deriveFont(16f)
    } else {
        g2.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16)
    }
    val fm = g2.fontMetrics
    val textWidth = fm.stringWidth(text)
    g2.drawString(text, (400 - textWidth) / 2, 270)
}

class SplashWindow(val window: JWindow) {
    @Volatile private var text = "Starting Plugin Toolkit..."
    @Volatile private var running = true
    private var renderThread: Thread? = null

    init {
        var logo: Image? = null
        val localFile = File("shared/gui/src/commonMain/composeResources/drawable/splash_logo.png")
        if (localFile.exists()) {
            logo = ImageIcon(localFile.absolutePath).image
        } else {
            val resourceUrl = Thread.currentThread().contextClassLoader.getResource(
                "composeResources/plugintoolkit.composeapp.generated.resources/drawable/splash_logo.png"
            )
            if (resourceUrl != null) {
                logo = ImageIcon(resourceUrl).image
            }
        }

        if (!window.isDisplayable) {
            try {
                window.addNotify()
            } catch (_: Throwable) {}
        }

        try {
            window.createBufferStrategy(2)
        } catch (_: Throwable) {}

        val bs = window.bufferStrategy

        if (bs != null) {
            renderThread = kotlin.concurrent.thread(isDaemon = true, name = "SplashRenderThread") {
                while (running && window.isDisplayable) {
                    try {
                        val g = bs.drawGraphics as? Graphics2D ?: continue
                        renderSplashFrame(g, text, logo)
                        g.dispose()
                        if (!bs.contentsLost()) {
                            bs.show()
                        }
                        Thread.sleep(16)
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
        }
    }

    fun updateText(newText: String) {
        text = newText
    }

    fun dispose() {
        running = false
        renderThread?.interrupt()

        val closeAction = Runnable {
            try {
                window.isVisible = false
                window.dispose()
            } catch (_: Throwable) {}
        }

        if (SwingUtilities.isEventDispatchThread()) {
            closeAction.run()
        } else {
            SwingUtilities.invokeLater(closeAction)
        }
    }
}

fun showSplashWindow(): SplashWindow {
    val window = JWindow()
    window.size = Dimension(400, 300)
    window.setLocationRelativeTo(null) // center on screen
    window.background = java.awt.Color(30, 30, 30)
    window.shape = RoundRectangle2D.Double(0.0, 0.0, 400.0, 300.0, 32.0, 32.0)
    window.ignoreRepaint = true
    window.isVisible = true
    return SplashWindow(window)
}
