package org.wip.plugintoolkit.features.flows

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.FlowExecutionGuard
import org.wip.plugintoolkit.features.flows.logic.FlowReadOnlyViolationException
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowExecutionGuardTest {

    private val jobManager = mockk<JobManager>(relaxed = true)
    private val guard = FlowExecutionGuard { jobManager }

    private fun createSubflowNode(id: Long, subflowName: String): Node.SubFlowNode {
        return Node.SubFlowNode(
            id = id,
            position = Offset.Zero,
            flowName = subflowName,
            inputs = emptyList(),
            outputs = emptyList()
        )
    }

    @Test
    fun testFlowNotLockedWhenNoActiveJobs() {
        every { jobManager.jobs } returns MutableStateFlow(emptyList())

        val flow = Flow(name = "MyFlow")
        assertFalse(guard.isFlowLocked("MyFlow", listOf(flow)))
        guard.assertCanMutate("MyFlow", listOf(flow)) // Should not throw
    }

    @Test
    fun testDirectRunningFlowIsLocked() {
        val runningJob = BackgroundJob(
            id = "job-1",
            name = "Flow: MyFlow",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = "MyFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(runningJob))

        val flow = Flow(name = "MyFlow")
        assertTrue(guard.isFlowLocked("MyFlow", listOf(flow)))

        assertFailsWith<FlowReadOnlyViolationException> {
            guard.assertCanMutate("MyFlow", listOf(flow))
        }
    }

    @Test
    fun testQueuedFlowIsLocked() {
        val queuedJob = BackgroundJob(
            id = "job-2",
            name = "Flow: QueuedFlow",
            type = JobType.Flow,
            status = JobStatus.Queued,
            pluginId = "system",
            capabilityName = "QueuedFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(queuedJob))

        val flow = Flow(name = "QueuedFlow")
        assertTrue(guard.isFlowLocked("QueuedFlow", listOf(flow)))

        assertFailsWith<FlowReadOnlyViolationException> {
            guard.assertCanMutate("QueuedFlow", listOf(flow))
        }
    }

    @Test
    fun testTransitiveSubflowIsLockedWhenParentFlowIsRunning() {
        // RootFlow runs SubFlowA, which runs SubFlowB
        val subFlowB = Flow(name = "SubFlowB")
        val subFlowA = Flow(
            name = "SubFlowA",
            nodes = listOf(createSubflowNode(1, "SubFlowB"))
        )
        val rootFlow = Flow(
            name = "RootFlow",
            nodes = listOf(createSubflowNode(2, "SubFlowA"))
        )

        val runningRootJob = BackgroundJob(
            id = "job-root",
            name = "Flow: RootFlow",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = "RootFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(runningRootJob))

        val allFlows = listOf(rootFlow, subFlowA, subFlowB)

        assertTrue(guard.isFlowLocked("RootFlow", allFlows))
        assertTrue(guard.isFlowLocked("SubFlowA", allFlows))
        assertTrue(guard.isFlowLocked("SubFlowB", allFlows))
        assertFalse(guard.isFlowLocked("UnrelatedFlow", allFlows))

        assertFailsWith<FlowReadOnlyViolationException> {
            guard.assertCanMutate("SubFlowB", allFlows)
        }
    }
}
