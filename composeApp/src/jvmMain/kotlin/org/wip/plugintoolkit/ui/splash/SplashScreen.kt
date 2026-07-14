package org.wip.plugintoolkit.ui.splash

import java.awt.AlphaComposite
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
import kotlin.concurrent.thread

class SplashWindow(val window: JWindow) {
    @Volatile private var text = "Starting Plugin Toolkit..."
    @Volatile private var running = true

    init {
        var logo: Image? = null
        val localFile = File("composeApp/src/commonMain/composeResources/drawable/splash_logo.png")
        if (localFile.exists()) {
            logo = ImageIcon(localFile.absolutePath).image
        } else {
            val resourceUrl = Thread.currentThread().contextClassLoader.getResource("composeResources/plugintoolkit.composeapp.generated.resources/drawable/splash_logo.png")
            if (resourceUrl != null) {
                logo = ImageIcon(resourceUrl).image
            }
        }

        // BufferStrategy requires the window to be displayable (isVisible = true handles this)
        window.createBufferStrategy(2)
        val bs = window.bufferStrategy

        thread(isDaemon = true, name = "SplashRenderThread") {
            var lastTime = System.nanoTime()

            while (running && window.isDisplayable) {
                try {
                    val currentTime = System.nanoTime()
                    val deltaMs = (currentTime - lastTime) / 1_000_000.0
                    if (deltaMs > 32.0) {
                        println("Splash active frame delta: ${deltaMs.toInt()} ms (Stutter!)")
                    }
                    lastTime = currentTime

                    val g = bs.drawGraphics as Graphics2D
                    
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

                    // Draw background
                    g.color = java.awt.Color(30, 30, 30)
                    g.fillRoundRect(0, 0, 400, 300, 32, 32)

                    // Draw logo
                    if (logo != null) {
                        g.drawImage(logo, 152, 40, 96, 96, null)
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
                    
                    g.stroke = BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.color = java.awt.Color(100, 181, 246)
                    val arc = Arc2D.Double(168.0, 168.0, 64.0, 64.0, start, -extent, Arc2D.OPEN)
                    g.draw(arc)

                    // Draw text
                    g.color = java.awt.Color.WHITE
                    if (g.font != null) {
                        g.font = g.font.deriveFont(16f)
                    } else {
                        g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16)
                    }
                    val fm = g.fontMetrics
                    val textWidth = fm.stringWidth(text)
                    g.drawString(text, (400 - textWidth) / 2, 270)

                    g.dispose()
                    if (!bs.contentsLost()) {
                        bs.show()
                    }
                    Thread.sleep(16)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Window disposed or rendering failed
                    break
                }
            }
        }
    }

    fun updateText(newText: String) {
        text = newText
    }
}

fun showSplashWindow(): SplashWindow {
    val window = JWindow()
    window.size = Dimension(400, 300)
    window.setLocationRelativeTo(null) // center on screen
    window.background = java.awt.Color(30, 30, 30) // OPAQUE background, BufferStrategy requires it
    window.shape = RoundRectangle2D.Double(0.0, 0.0, 400.0, 300.0, 32.0, 32.0) // OS trims the corners natively
    
    // Ignore OS repaints to fully dedicate drawing to our custom thread
    window.ignoreRepaint = true 
    window.isVisible = true
    
    return SplashWindow(window)
}
