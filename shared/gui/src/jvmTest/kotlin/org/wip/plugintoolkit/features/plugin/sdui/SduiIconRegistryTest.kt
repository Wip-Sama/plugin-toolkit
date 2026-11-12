package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import kotlin.test.Test
import kotlin.test.assertEquals

class SduiIconRegistryTest {

    @Test
    fun testResolvesStandardSemanticNames() {
        assertEquals(Icons.Default.Upload, SduiIconRegistry.resolve("upload"))
        assertEquals(Icons.Default.Download, SduiIconRegistry.resolve("download"))
        assertEquals(Icons.Default.Sync, SduiIconRegistry.resolve("sync"))
        assertEquals(Icons.Default.Check, SduiIconRegistry.resolve("check"))
        assertEquals(Icons.Default.CheckCircle, SduiIconRegistry.resolve("check_circle"))
        assertEquals(Icons.Default.Warning, SduiIconRegistry.resolve("warning"))
        assertEquals(Icons.Default.Error, SduiIconRegistry.resolve("error"))
        assertEquals(Icons.Default.Book, SduiIconRegistry.resolve("book"))
        assertEquals(Icons.Default.Book, SduiIconRegistry.resolve("chapter"))
        assertEquals(Icons.Default.Delete, SduiIconRegistry.resolve("delete"))
        assertEquals(Icons.Default.Edit, SduiIconRegistry.resolve("edit"))
        assertEquals(Icons.Default.Add, SduiIconRegistry.resolve("zoom_in"))
        assertEquals(Icons.Default.Remove, SduiIconRegistry.resolve("zoom_out"))
        assertEquals(Icons.Default.Layers, SduiIconRegistry.resolve("layers"))
    }

    @Test
    fun testNormalizesNamesWithDashesSpacesAndMixedCase() {
        assertEquals(Icons.Default.Add, SduiIconRegistry.resolve("Zoom-In"))
        assertEquals(Icons.Default.Remove, SduiIconRegistry.resolve("ZOOM OUT"))
        assertEquals(Icons.Default.CheckCircle, SduiIconRegistry.resolve("Check-Circle"))
        assertEquals(Icons.Default.Sync, SduiIconRegistry.resolve("  Sync  "))
    }

    @Test
    fun testGracefulFallbackForUnknownOrEmptyNames() {
        assertEquals(Icons.Default.Info, SduiIconRegistry.resolve(null))
        assertEquals(Icons.Default.Info, SduiIconRegistry.resolve(""))
        assertEquals(Icons.Default.Info, SduiIconRegistry.resolve("   "))
        assertEquals(Icons.Default.Info, SduiIconRegistry.resolve("completely_unknown_icon_xyz"))
    }
}
