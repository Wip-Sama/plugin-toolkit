package com.wip.slicer

import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.PluginModule
import com.wip.plugin.api.annotations.CapabilityParam
import com.wip.plugin.api.PluginLogger
import com.wip.plugin.api.ProgressReporter
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import java.awt.image.BufferedImage

@PluginModule(
    id = "com.wip.operations.slicer",
    name = "Slicer",
    version = "0.2.0",
    description = "A module that provides vertical images sliding capabilities for manhwa."
)
class Slicer {
    @Capability(
        name = "slicer",
        description = "Slices a list of images"
    )
    fun slicer(
        @CapabilityParam(description = "List of items to slice") folderPath: String,
        @CapabilityParam(description = "Minimum height", defaultValue = "1000") minHeight: Int,
        @CapabilityParam(description = "Desired Height", defaultValue = "3000") desiredHeight: Int,
        @CapabilityParam(description = "Maximum Height", defaultValue = "5000") maxHeight: Int,
        @CapabilityParam(description = "Prioritize smaller", defaultValue = "false") prioritizeSmallerImages: Boolean,
        @CapabilityParam(description = "Cut tolerance", defaultValue = "10") cutTolerance: Int,
        logger: PluginLogger,
        progressReporter: ProgressReporter
    ): String {
        logger.log("Starting slicer for folder: $folderPath")
        progressReporter.report(0.1f)
        val files = File(folderPath).listFiles()
        if (files == null || files.isEmpty()) return "No files found"
        val images = files.filter { it.isFile && it.extension in listOf("jpg", "jpeg", "png") }
        val sortedImages = images.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }
        if (sortedImages.isEmpty()) return "No valid images found"

        // Combine images into one large buffer conceptually to handle merging small ones
        val firstImage = ImageIO.read(sortedImages[0])
        val width = firstImage.width
        val fullBitmap = mutableListOf<BufferedImage>()

        val usefulRowVarianceList = analyzeRowVariances(sortedImages, width, cutTolerance, fullBitmap)
        val totalHeight = usefulRowVarianceList.size
        progressReporter.report(0.5f)

        val (finalCuts, totalError) = findOptimalCuts(totalHeight, usefulRowVarianceList, minHeight, desiredHeight, maxHeight, prioritizeSmallerImages)

        if (finalCuts.isEmpty()) return "Could not find a valid slicing path"

        val outputDir = File(File(folderPath).parentFile, "output")
        saveSlices(fullBitmap, width, totalHeight, finalCuts, outputDir)

        val displayError = totalError.toDouble() / 1000.0
        progressReporter.report(1.0f)
        logger.log("Slicing completed. Error: $displayError")
        return "Path: ${outputDir.absolutePath}. Slices created: ${finalCuts.size}. Total cumulative error: $displayError."
    }

    private fun analyzeRowVariances(
        sortedImages: List<File>,
        width: Int,
        cutTolerance: Int,
        fullBitmap: MutableList<BufferedImage>
    ): List<Boolean> {
        val rowVarianceList = mutableListOf<Int>()
        sortedImages.forEach { image ->
            val bufferedImage = ImageIO.read(image)
            fullBitmap.add(bufferedImage)
            for (y in 0 until bufferedImage.height) {
                var maxDiff = 0
                for (x in 0 until width - 1) {
                    if (x >= bufferedImage.width - 1) break
                    val pixel1 = bufferedImage.getRGB(x, y)
                    val pixel2 = bufferedImage.getRGB(x + 1, y)

                    val rDiff = abs((pixel1 shr 16 and 0xFF) - (pixel2 shr 16 and 0xFF))
                    val gDiff = abs((pixel1 shr 8 and 0xFF) - (pixel2 shr 8 and 0xFF))
                    val bDiff = abs((pixel1 and 0xFF) - (pixel2 and 0xFF))

                    val currentMax = rDiff.coerceAtLeast(gDiff.coerceAtLeast(bDiff))
                    if (currentMax > maxDiff) maxDiff = currentMax
                }
                rowVarianceList.add(maxDiff)
            }
        }
        return rowVarianceList.map { it <= cutTolerance }
    }

    private fun findOptimalCuts(
        totalHeight: Int,
        usefulRowVarianceList: List<Boolean>,
        minHeight: Int,
        desiredHeight: Int,
        maxHeight: Int,
        prioritizeSmallerImages: Boolean
    ): Pair<List<Int>, Long> {
        val dp = LongArray(totalHeight + 1) { Long.MAX_VALUE }
        val parent = IntArray(totalHeight + 1) { -1 }
        dp[0] = 0

        for (i in 0 until totalHeight) {
            if (dp[i] == Long.MAX_VALUE) continue

            val searchStart = (i + minHeight.coerceAtLeast(1)).coerceAtMost(totalHeight)
            val searchEnd = (i + maxHeight).coerceAtMost(totalHeight)

            for (j in searchStart..searchEnd) {
                if ((j > 0 && j <= totalHeight && usefulRowVarianceList[j - 1]) || j == totalHeight) {
                    val sliceHeight = j - i
                    val diff = sliceHeight - desiredHeight
                    val absDiff = abs(diff)

                    // Primary error: absolute difference scaled for sub-pixel penalties
                    var currentError = absDiff.toLong() * 1000L

                    // 1. "Perfect Cut" Bias: Penalty for any non-zero deviation.
                    // This ensures hitting exactly desiredHeight is preferred over balancing multiple offsets.
                    if (absDiff > 0) {
                        currentError += 500L
                    }

                    // 2. Asymmetric Penalty for prioritizeSmallerImages:
                    // Penalize "over" slices more than "under" slices.
                    if (prioritizeSmallerImages && diff > 0) {
                        currentError += absDiff.toLong() * 200L // 20% extra penalty for being over
                    }

                    val totalError = dp[i] + currentError

                    // We use strictly less-than. This preserves the "best early" path in case of remaining ties,
                    // since the outer loop 'i' visits earlier starting points first.
                    if (totalError < dp[j]) {
                        dp[j] = totalError
                        parent[j] = i
                    }
                }
            }
        }

        if (dp[totalHeight] == Long.MAX_VALUE) return emptyList<Int>() to -1L

        val cuts = mutableListOf<Int>()
        var curr = totalHeight
        while (curr > 0) {
            cuts.add(curr)
            curr = parent[curr]
        }
        return cuts.reversed() to dp[totalHeight]
    }

    private fun saveSlices(
        fullBitmap: List<BufferedImage>,
        width: Int,
        totalHeight: Int,
        finalCuts: List<Int>,
        outputDir: File
    ) {
        if (!outputDir.exists()) outputDir.mkdirs()

        val combinedImage = BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_RGB)
        val g = combinedImage.createGraphics()
        var currentY = 0
        fullBitmap.forEach { img ->
            g.drawImage(img, 0, currentY, null)
            currentY += img.height
        }
        g.dispose()

        var prevCut = 0
        finalCuts.forEachIndexed { index, cut ->
            val sliceHeight = cut - prevCut
            val slice = combinedImage.getSubimage(0, prevCut, width, sliceHeight)
            val outputFile = File(outputDir, "${index + 1}.png")
            ImageIO.write(slice, "png", outputFile)
            prevCut = cut
        }
    }
}