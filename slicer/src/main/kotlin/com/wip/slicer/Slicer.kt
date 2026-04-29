package com.wip.slicer

import com.wip.plugin.api.annotations.Capability
import com.wip.plugin.api.annotations.PluginModule
import com.wip.plugin.api.annotations.CapabilityParam
import com.wip.plugin.api.PluginLogger
import com.wip.plugin.api.ProgressReporter
import kotlinx.io.asInputStream
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.Path
import kotlinx.io.buffered
import javax.imageio.ImageIO
import kotlin.math.abs
import java.awt.image.BufferedImage

@PluginModule(
    id = "com.wip.operations.slicer",
    name = "Slicer",
    version = "0.4.2",
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
        @CapabilityParam(description = "Desired Height", defaultValue = "10000") desiredHeight: Int,
        @CapabilityParam(description = "Maximum Height", defaultValue = "10000") maxHeight: Int,
        @CapabilityParam(description = "Prioritize smaller", defaultValue = "true") prioritizeSmallerImages: Boolean,
        @CapabilityParam(description = "Cut tolerance", defaultValue = "5") cutTolerance: Int,
        logger: PluginLogger,
        progressReporter: ProgressReporter
    ): String {
        logger.log("Starting slicer for folder: $folderPath")
        progressReporter.report(0.1f)
        
        val folder = Path(folderPath)
        val files = SystemFileSystem.list(folder).toList()
        
        if (files.isEmpty()) return "No files found"
        val images = files.filter { path ->
            val metadata = SystemFileSystem.metadataOrNull(path)
            metadata?.isRegularFile == true && path.name.lowercase().let { name ->
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            }
        }
        val sortedImages = images.sortedWith(natsortComparator)
        if (sortedImages.isEmpty()) return "No valid images found"

        // Combine images into one large buffer conceptually to handle merging small ones
        val firstImage = SystemFileSystem.source(sortedImages[0]).buffered().asInputStream().use { ImageIO.read(it) }
        val width = firstImage.width
        val fullBitmap = mutableListOf<BufferedImage>()

        val usefulRowVarianceList = analyzeRowVariances(sortedImages, width, cutTolerance, fullBitmap)
        val totalHeight = usefulRowVarianceList.size
        progressReporter.report(0.5f)

        val (finalCuts, totalError) = findOptimalCuts(totalHeight, usefulRowVarianceList, minHeight, desiredHeight, maxHeight, prioritizeSmallerImages)

        if (finalCuts.isEmpty()) return "Could not find a valid slicing path"

        val parentFolder = folder.parent ?: folder
        val outputDir = Path("${parentFolder}/output")
        saveSlices(fullBitmap, width, totalHeight, finalCuts, outputDir)

        val displayError = totalError.toDouble() / 1000.0
        progressReporter.report(1.0f)
        logger.log("Slicing completed. Error: $displayError")
        return "Path: ${outputDir}. Slices created: ${finalCuts.size}. Total cumulative error: $displayError."
    }

    private fun analyzeRowVariances(
        sortedImages: List<Path>,
        width: Int,
        cutTolerance: Int,
        fullBitmap: MutableList<BufferedImage>
    ): List<Boolean> {
        val rowVarianceList = mutableListOf<Int>()
        sortedImages.forEach { imagePath ->
            val bufferedImage = SystemFileSystem.source(imagePath).buffered().asInputStream().use { ImageIO.read(it) }
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

        val FORCED_CUT_PENALTY = 1_000_000_000_000L

        for (i in 0 until totalHeight) {
            if (dp[i] == Long.MAX_VALUE) continue

            val searchStart = (i + minHeight.coerceAtLeast(1)).coerceAtMost(totalHeight)
            val searchEnd = (i + maxHeight).coerceAtMost(totalHeight)

            // Forced cut target is desiredHeight, but clamped to search range to respect min/max if possible
            val forcedJ = (i + desiredHeight).coerceIn(searchStart, searchEnd)

            for (j in searchStart..searchEnd) {
                val isUseful = (j > 0 && j <= totalHeight && usefulRowVarianceList[j - 1]) || j == totalHeight
                val isForced = (j == forcedJ)

                if (isUseful || isForced) {
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

                    // 3. Forced Cut Penalty: Apply if we are forcing a cut on a non-useful row
                    if (!isUseful && isForced) {
                        currentError += FORCED_CUT_PENALTY
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
        outputDir: Path
    ) {
        if (!SystemFileSystem.exists(outputDir)) SystemFileSystem.createDirectories(outputDir)

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
            val outputFilePath = Path("${outputDir}/${index + 1}.png")
            // ImageIO.write doesn't take Sink, but we can use File if needed or OutputStream
            // Since it's JVM only, we can use File(outputFilePath.toString())
            ImageIO.write(slice, "png", java.io.File(outputFilePath.toString()))
            prevCut = cut
        }
    }

    private val natsortComparator = object : Comparator<Path> {
        override fun compare(file1: Path, file2: Path): Int {
            val s1 = file1.name
            val s2 = file2.name
            var i = 0
            var j = 0
            while (i < s1.length && j < s2.length) {
                val c1 = s1[i]
                val c2 = s2[j]
                if (c1.isDigit() && c2.isDigit()) {
                    var num1 = ""
                    while (i < s1.length && s1[i].isDigit()) num1 += s1[i++]
                    var num2 = ""
                    while (j < s2.length && s2[j].isDigit()) num2 += s2[j++]
                    val n1 = num1.toLongOrNull() ?: Long.MAX_VALUE
                    val n2 = num2.toLongOrNull() ?: Long.MAX_VALUE
                    if (n1 != n2) return n1.compareTo(n2)
                    if (num1.length != num2.length) return num1.length.compareTo(num2.length)
                } else {
                    val lc1 = c1.lowercaseChar()
                    val lc2 = c2.lowercaseChar()
                    if (lc1 != lc2) return lc1.compareTo(lc2)
                    i++
                    j++
                }
            }
            return s1.length.compareTo(s2.length)
        }
    }
}
