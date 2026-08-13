package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.wip.plugintoolkit.api.Capability

sealed class CapabilityLockStatus {
    object Unlocked : CapabilityLockStatus()
    data class Locked(
        val missingLocks: List<String>,
        val missingSettings: List<String>
    ) : CapabilityLockStatus() {
        val isLocked: Boolean get() = true
    }
}

object CapabilityLockUtils {
    fun checkCapabilityLockStatus(
        capability: Capability,
        providedLocks: Map<String, Boolean>,
        providedSettings: Map<String, JsonElement>
    ): CapabilityLockStatus {
        val missingLocks = capability.requiredLocks.filter { providedLocks[it] != true }
        val missingSettings = capability.requiresSettings.filter { settingKey ->
            val v = providedSettings[settingKey]
            v == null || v is JsonNull || v.toString().replace("\"", "").isBlank()
        }
        return if (missingLocks.isEmpty() && missingSettings.isEmpty()) {
            CapabilityLockStatus.Unlocked
        } else {
            CapabilityLockStatus.Locked(missingLocks, missingSettings)
        }
    }
}
