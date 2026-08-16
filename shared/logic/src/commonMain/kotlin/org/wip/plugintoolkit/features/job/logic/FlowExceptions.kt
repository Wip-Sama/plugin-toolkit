package org.wip.plugintoolkit.features.job.logic

import kotlinx.serialization.json.JsonElement

class PauseFlowException(val resumeState: JsonElement) : RuntimeException("Flow execution paused")
