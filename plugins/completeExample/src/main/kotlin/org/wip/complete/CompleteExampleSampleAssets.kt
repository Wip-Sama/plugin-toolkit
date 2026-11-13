package org.wip.complete

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.BoardLayer
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

data class BoardAssetBundle(
    val backgroundAsset: AssetReference,
    val stampAsset: AssetReference
)

/**
 * Generates sample raster images for the interactive spatial board demo and writes
 * composited output artifacts into the plugin's sandboxed filesystem.
 */
object CompleteExampleSampleAssets {

    private const val PAGE_ASSET_PATH = "assets/sample_manhwa_page.png"
    private const val STAMP_ASSET_PATH = "assets/sample_stamp.png"
    internal const val EXPORT_IMAGE_PATH = "exports/composited_page.png"
    internal const val EXPORT_REPORT_PATH = "exports/composited_page.json"

    private const val PAGE_WIDTH = 720
    private const val PAGE_HEIGHT = 960
    private const val STAMP_WIDTH = 200
    private const val STAMP_HEIGHT = 80

    private val prettyJson = Json { prettyPrint = true }

    suspend fun ensureBoardAssets(context: PluginContext): BoardAssetBundle {
        val pageBytes = renderSamplePagePng()
        val stampBytes = renderSampleStampPng()

        val pageWritten = runCatching {
            val relPath = RelativePath.from(PAGE_ASSET_PATH).getOrThrow()
            context.fileSystem.writeFile(relPath, pageBytes).isSuccess
        }.getOrDefault(false)

        val stampWritten = runCatching {
            val relPath = RelativePath.from(STAMP_ASSET_PATH).getOrThrow()
            context.fileSystem.writeFile(relPath, stampBytes).isSuccess
        }.getOrDefault(false)

        val backgroundAsset = if (pageWritten) {
            AssetReference.Relative(PAGE_ASSET_PATH)
        } else {
            AssetReference.Uri("data:image/png;base64," + Base64.getEncoder().encodeToString(pageBytes))
        }

        val stampAsset = if (stampWritten) {
            AssetReference.Relative(STAMP_ASSET_PATH)
        } else {
            AssetReference.Uri("data:image/png;base64," + Base64.getEncoder().encodeToString(stampBytes))
        }

        return BoardAssetBundle(
            backgroundAsset = backgroundAsset,
            stampAsset = stampAsset
        )
    }

    suspend fun exportCompositedPage(
        context: PluginContext,
        boardTitle: String,
        layers: List<BoardLayer>
    ): String {
        val baseImage = createBasePageImage()
        val g = baseImage.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            for (layer in layers.filter { it.visible }.sortedBy { it.zIndex }) {
                when (layer) {
                    is BoardLayer.ShapeLayer -> {
                        val x = (layer.position.x * PAGE_WIDTH).toInt()
                        val y = (layer.position.y * PAGE_HEIGHT).toInt()
                        val w = (layer.size.width * PAGE_WIDTH).toInt().coerceAtLeast(24)
                        val h = (layer.size.height * PAGE_HEIGHT).toInt().coerceAtLeast(24)
                        g.color = Color(250, 250, 255, 225)
                        g.fillRoundRect(x, y, w, h, 24, 24)
                        g.color = Color(79, 70, 229)
                        g.stroke = BasicStroke(layer.strokeWidth.coerceIn(1f, 8f))
                        g.drawRoundRect(x, y, w, h, 24, 24)
                        layer.text?.let { label ->
                            g.color = Color(24, 24, 27)
                            g.font = Font("SansSerif", Font.BOLD, 18)
                            g.drawString(label, x + 14, y + (h / 2) + 6)
                        }
                    }
                    is BoardLayer.TextLayer -> {
                        val x = (layer.position.x * PAGE_WIDTH).toInt()
                        val y = (layer.position.y * PAGE_HEIGHT).toInt()
                        g.color = Color(15, 23, 42, 220)
                        g.fillRoundRect(x, y, 280, 44, 12, 12)
                        g.color = Color(248, 250, 252)
                        g.font = Font("SansSerif", Font.BOLD, 18)
                        g.drawString(layer.text, x + 12, y + 28)
                    }
                    is BoardLayer.ImageLayer -> {
                        val x = (layer.position.x * PAGE_WIDTH).toInt()
                        val y = (layer.position.y * PAGE_HEIGHT).toInt()
                        val w = (layer.size.width * PAGE_WIDTH).toInt().coerceAtLeast(32)
                        val h = (layer.size.height * PAGE_HEIGHT).toInt().coerceAtLeast(24)
                        g.color = Color(16, 185, 129, 200)
                        g.fillRoundRect(x, y, w, h, 14, 14)
                        g.color = Color.WHITE
                        g.font = Font("SansSerif", Font.BOLD, 15)
                        g.drawString("STAMP", x + 12, y + (h / 2) + 5)
                    }
                    is BoardLayer.ComponentLayer -> {
                        val x = (layer.position.x * PAGE_WIDTH).toInt()
                        val y = (layer.position.y * PAGE_HEIGHT).toInt()
                        val w = ((layer.size?.width ?: 0.25f) * PAGE_WIDTH).toInt()
                        val h = ((layer.size?.height ?: 0.14f) * PAGE_HEIGHT).toInt()
                        g.color = Color(30, 41, 59, 210)
                        g.fillRoundRect(x, y, w, h, 16, 16)
                        g.color = Color(56, 189, 248)
                        g.drawString("COMPONENT: ${layer.id}", x + 12, y + 26)
                    }
                    is BoardLayer.PathLayer -> {
                        val pts = layer.path.points
                        if (pts.size >= 2) {
                            g.color = Color(239, 68, 68)
                            g.stroke = BasicStroke(layer.path.strokeWidth.coerceIn(2f, 12f))
                            for (i in 0 until pts.size - 1) {
                                g.drawLine(
                                    (pts[i].x * PAGE_WIDTH).toInt(),
                                    (pts[i].y * PAGE_HEIGHT).toInt(),
                                    (pts[i + 1].x * PAGE_WIDTH).toInt(),
                                    (pts[i + 1].y * PAGE_HEIGHT).toInt()
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            g.dispose()
        }

        val outBytes = ByteArrayOutputStream().use { baos ->
            ImageIO.write(baseImage, "png", baos)
            baos.toByteArray()
        }

        val metadataSummary = mapOf(
            "boardTitle" to boardTitle,
            "layerCount" to layers.size.toString(),
            "outputImage" to EXPORT_IMAGE_PATH
        )

        runCatching {
            val imgPath = RelativePath.from(EXPORT_IMAGE_PATH).getOrThrow()
            context.fileSystem.writeFile(imgPath, outBytes)
            val reportPath = RelativePath.from(EXPORT_REPORT_PATH).getOrThrow()
            context.fileSystem.writeTextFile(reportPath, prettyJson.encodeToString(metadataSummary))
        }

        return EXPORT_IMAGE_PATH
    }

    private fun renderSamplePagePng(): ByteArray {
        val image = createBasePageImage()
        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "png", baos)
            baos.toByteArray()
        }
    }

    private fun createBasePageImage(): BufferedImage {
        val image = BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Page paper background
            g.color = Color(245, 245, 250)
            g.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT)

            // Subtle header strip
            g.color = Color(24, 24, 32)
            g.font = Font("SansSerif", Font.BOLD, 16)
            g.drawString("SAMPLE STORYBOARD // PAGE 01", 32, 34)

            // Panel 1: Establishing horizon + portal glow
            drawPanel(g, 32, 52, PAGE_WIDTH - 64, 260, Color(30, 41, 59), Color(79, 70, 229))
            g.color = Color(224, 231, 255, 190)
            g.fillOval(420, 96, 130, 130)
            g.color = Color(15, 23, 42)
            g.fillPolygon(intArrayOf(80, 240, 380, 80), intArrayOf(312, 190, 312, 312), 4)

            // Panel 2: Mid-shot duel silhouettes
            drawPanel(g, 32, 332, PAGE_WIDTH - 64, 280, Color(55, 48, 163), Color(124, 58, 237))
            g.color = Color(255, 255, 255, 90)
            g.stroke = BasicStroke(3f)
            g.drawLine(90, 470, 620, 420)
            g.drawLine(120, 520, 580, 480)

            // Panel 3: Split bottom panel
            drawPanel(g, 32, 632, 310, 290, Color(15, 23, 42), Color(14, 116, 144))
            drawPanel(g, 362, 632, 326, 290, Color(69, 10, 10), Color(185, 28, 28))
        } finally {
            g.dispose()
        }
        return image
    }

    private fun drawPanel(
        g: java.awt.Graphics2D,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        topColor: Color,
        bottomColor: Color
    ) {
        g.paint = GradientPaint(x.toFloat(), y.toFloat(), topColor, x.toFloat(), (y + h).toFloat(), bottomColor)
        g.fillRoundRect(x, y, w, h, 14, 14)
        g.color = Color(15, 23, 42)
        g.stroke = BasicStroke(4f)
        g.drawRoundRect(x, y, w, h, 14, 14)
    }

    private fun renderSampleStampPng(): ByteArray {
        val image = BufferedImage(STAMP_WIDTH, STAMP_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(16, 185, 129, 230)
            g.fillRoundRect(4, 4, STAMP_WIDTH - 8, STAMP_HEIGHT - 8, 16, 16)
            g.color = Color(6, 78, 59)
            g.stroke = BasicStroke(3f)
            g.drawRoundRect(4, 4, STAMP_WIDTH - 8, STAMP_HEIGHT - 8, 16, 16)
            g.color = Color.WHITE
            g.font = Font("SansSerif", Font.BOLD, 18)
            g.drawString("QA APPROVED", 28, 47)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { baos ->
            ImageIO.write(image, "png", baos)
            baos.toByteArray()
        }
    }
}
