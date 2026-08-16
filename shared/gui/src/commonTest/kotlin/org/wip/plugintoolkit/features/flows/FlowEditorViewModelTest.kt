package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.utils.BoardMathUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowEditorViewModelTest {

    @Test
    fun testPanCalculation() {
        val initialState = FlowEditorState(offset = Offset(100f, 200f))
        val panDelta = Offset(50f, -30f)
        val newOffset = initialState.offset + panDelta

        assertEquals(Offset(150f, 170f), newOffset)
    }

    @Test
    fun testZoomFocusCalculation() {
        val initialScale = 1.0f
        val initialOffset = Offset(0f, 0f)
        val focusPosition = Offset(300f, 100f)
        val newScale = 2.0f

        val boardFocus = (focusPosition - initialOffset) / initialScale
        val newOffset = focusPosition - boardFocus * newScale

        assertEquals(Offset(300f, 100f), boardFocus)
        assertEquals(Offset(-300f, -100f), newOffset)

        // Verify model position mapping invariant
        val nodeModelPos = Offset(300f, 100f)
        val renderedBefore = nodeModelPos * initialScale + initialOffset
        val renderedAfter = nodeModelPos * newScale + newOffset

        assertEquals(focusPosition, renderedBefore)
        assertEquals(focusPosition, renderedAfter)
    }

    @Test
    fun testModelToWindowCoordinateTransformation() {
        val modelPos = Offset(100f, 100f)
        val scale = 1.5f
        val boardOffset = Offset(50f, 50f)

        val windowPos = modelPos * scale + boardOffset
        val reconstructedModelPos = (windowPos - boardOffset) / scale

        assertEquals(Offset(200f, 200f), windowPos)
        assertEquals(modelPos, reconstructedModelPos)
    }

    @Test
    fun testDistanceToBezierCalculation() {
        val p0 = Offset(0f, 0f)
        val p3 = Offset(100f, 0f)
        val midPoint = Offset(50f, 0f)

        val distance = BoardMathUtils.getDistanceToBezier(midPoint, p0, p3)
        assertTrue(distance < 5f, "Distance to bezier on straight line should be near zero")
    }
}
