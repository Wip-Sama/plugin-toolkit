package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement

class PauseFlowException(val resumeState: JsonElement) : CancellationException("Flow execution paused")
