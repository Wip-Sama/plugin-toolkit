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
    fun isLockSatisfied(lockKey: String, providedLocks: Map<String, Boolean>): Boolean {
        return providedLocks[lockKey] == true ||
                providedLocks[lockKey.lowercase()] == true ||
                providedLocks.any { (k, v) -> k.equals(lockKey, ignoreCase = true) && v }
    }

    fun checkCapabilityLockStatus(
        capability: Capability,
        providedLocks: Map<String, Boolean>,
        providedSettings: Map<String, JsonElement>
    ): CapabilityLockStatus {
        val missingLocks = capability.requiredLocks.filter { lockKey ->
            !isLockSatisfied(lockKey, providedLocks)
        }
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
