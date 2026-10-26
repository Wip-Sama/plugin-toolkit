package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.model.localized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidebarDataAndFilteringTest {

    @Test
    fun testSidebarElementWithAndWithoutDescription() {
        val standardElement = SidebarElement(
            id = "item1",
            icon = Icons.Default.Bolt,
            title = "Title Only".localized
        )
        assertEquals("Title Only", (standardElement.title as LocalizedString.Raw).text)
        assertNull(standardElement.description)

        val detailedElement = SidebarElement(
            id = "item2",
            icon = Icons.Default.Bolt,
            title = "Item Title".localized,
            description = "Detailed item description".localized
        )
        assertEquals("Item Title", (detailedElement.title as LocalizedString.Raw).text)
        assertEquals("Detailed item description", (detailedElement.description as LocalizedString.Raw).text)
    }

    @Test
    fun testDirectExecutionSidebarCapabilityFiltering() {
        val standardCap = Capability(
            name = "standardTask",
            description = "Background task",
            returnType = DataType.Primitive(PrimitiveType.STRING),
            isInteractive = false,
            context = CapabilityContext.ANY
        )

        val interactiveCap = Capability(
            name = "interactiveTool",
            description = "Interactive SDUI tool",
            returnType = DataType.Primitive(PrimitiveType.STRING),
            isInteractive = true,
            context = CapabilityContext.INTERACTIVE_ONLY
        )

        val flowOnlyCap = Capability(
            name = "flowTask",
            description = "Flow only task",
            returnType = DataType.Primitive(PrimitiveType.STRING),
            isInteractive = false,
            context = CapabilityContext.FLOW_ONLY
        )

        val capabilities = listOf(standardCap, interactiveCap, flowOnlyCap)

        // Automated (standard) filtering logic
        val automatedCaps = capabilities.filter { cap ->
            cap.context != CapabilityContext.FLOW_ONLY &&
                !cap.isInteractive &&
                cap.context != CapabilityContext.INTERACTIVE_ONLY
        }
        assertEquals(listOf(standardCap), automatedCaps)

        // Interactive filtering logic
        val interactiveCaps = capabilities.filter { cap ->
            cap.isInteractive || cap.context == CapabilityContext.INTERACTIVE_ONLY
        }
        assertEquals(listOf(interactiveCap), interactiveCaps)
    }
}
