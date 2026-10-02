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
import org.wip.plugintoolkit.features.flows.logic.getAllReferencedPluginIds
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
    private val guard = FlowExecutionGuard(jobManagerProvider = { jobManager })

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

    @Test
    fun testCollectReferencedPluginIdsDirectAndNestedSubflows() {
        val pluginA = PluginInfo(id = "org.wip.plugin.a", name = "A", version = "1.0", description = "")
        val pluginB = PluginInfo(id = "org.wip.plugin.b", name = "B", version = "1.0", description = "")

        val capNodeA = Node.CapabilityNode(
            id = 10,
            position = Offset.Zero,
            pluginInfo = pluginA,
            capability = Capability("capA", "desc", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = emptyList(),
            outputs = emptyList()
        )
        val capNodeB = Node.CapabilityNode(
            id = 20,
            position = Offset.Zero,
            pluginInfo = pluginB,
            capability = Capability("capB", "desc", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = emptyList(),
            outputs = emptyList()
        )

        val childFlow = Flow(name = "ChildFlow", nodes = listOf(capNodeB))
        val parentFlow = Flow(
            name = "ParentFlow",
            nodes = listOf(capNodeA, createSubflowNode(1, "ChildFlow"))
        )

        val allFlows = listOf(parentFlow, childFlow)
        val referencedPlugins = parentFlow.getAllReferencedPluginIds(allFlows)

        kotlin.test.assertEquals(setOf("org.wip.plugin.a", "org.wip.plugin.b"), referencedPlugins)
    }

    @Test
    fun testCollectReferencedPluginIdsHandlesCycles() {
        val pluginC = PluginInfo(id = "org.wip.plugin.c", name = "C", version = "1.0", description = "")
        val capNodeC = Node.CapabilityNode(
            id = 30,
            position = Offset.Zero,
            pluginInfo = pluginC,
            capability = Capability("capC", "desc", returnType = DataType.Primitive(PrimitiveType.STRING)),
            inputs = emptyList(),
            outputs = emptyList()
        )

        // Cyclic flows: Flow1 -> Flow2 -> Flow1
        val flow1 = Flow(name = "Flow1", nodes = listOf(capNodeC, createSubflowNode(1, "Flow2")))
        val flow2 = Flow(name = "Flow2", nodes = listOf(createSubflowNode(2, "Flow1")))

        val allFlows = listOf(flow1, flow2)
        val referencedPlugins = flow1.getAllReferencedPluginIds(allFlows)

        kotlin.test.assertEquals(setOf("org.wip.plugin.c"), referencedPlugins)
    }

    @Test
    fun testFlowLockedAndRunningWhenPauseRequested() {
        val pausingJob = BackgroundJob(
            id = "job-pause",
            name = "Flow: PausingFlow",
            type = JobType.Flow,
            status = JobStatus.PauseRequested,
            pluginId = "system",
            capabilityName = "PausingFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(pausingJob))

        val flow = Flow(name = "PausingFlow")
        assertTrue(guard.isFlowLocked("PausingFlow", listOf(flow)))
        assertTrue(guard.isFlowRunning("PausingFlow", listOf(flow)))
    }

    @Test
    fun testIsFlowRunningTransitive() {
        val runningJob = BackgroundJob(
            id = "job-root",
            name = "Flow: RootFlow",
            type = JobType.Flow,
            status = JobStatus.Running,
            pluginId = "system",
            capabilityName = "RootFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(runningJob))

        val subFlow = Flow(name = "ChildSubFlow")
        val rootFlow = Flow(
            name = "RootFlow",
            nodes = listOf(createSubflowNode(1, "ChildSubFlow"))
        )
        val allFlows = listOf(rootFlow, subFlow)

        assertTrue(guard.isFlowRunning("RootFlow", allFlows))
        assertTrue(guard.isFlowRunning("ChildSubFlow", allFlows))
        assertFalse(guard.isFlowRunning("UnrelatedFlow", allFlows))
    }

    @Test
    fun testDirectPausedFlowIsLockedAndPaused() {
        val pausedJob = BackgroundJob(
            id = "job-paused",
            name = "Flow: PausedFlow",
            type = JobType.Flow,
            status = JobStatus.Paused,
            pluginId = "system",
            capabilityName = "PausedFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(pausedJob))

        val flow = Flow(name = "PausedFlow")
        assertTrue(guard.isFlowLocked("PausedFlow", listOf(flow)))
        assertFalse(guard.isFlowRunning("PausedFlow", listOf(flow)))
        assertTrue(guard.isFlowPaused("PausedFlow", listOf(flow)))
    }

    @Test
    fun testIsFlowPausedTransitive() {
        val pausedJob = BackgroundJob(
            id = "job-root-paused",
            name = "Flow: RootFlow",
            type = JobType.Flow,
            status = JobStatus.Paused,
            pluginId = "system",
            capabilityName = "RootFlow"
        )
        every { jobManager.jobs } returns MutableStateFlow(listOf(pausedJob))

        val subFlow = Flow(name = "ChildSubFlow")
        val rootFlow = Flow(
            name = "RootFlow",
            nodes = listOf(createSubflowNode(1, "ChildSubFlow"))
        )
        val allFlows = listOf(rootFlow, subFlow)

        assertTrue(guard.isFlowLocked("RootFlow", allFlows))
        assertTrue(guard.isFlowLocked("ChildSubFlow", allFlows))
        assertTrue(guard.isFlowPaused("RootFlow", allFlows))
        assertTrue(guard.isFlowPaused("ChildSubFlow", allFlows))
        assertFalse(guard.isFlowPaused("UnrelatedFlow", allFlows))
    }
}
