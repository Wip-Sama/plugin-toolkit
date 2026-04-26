package com.wip.plugin.api

import org.koin.core.module.Module
import kotlinx.coroutines.flow.Flow

/**
 * The root interface for any plugin.
 */
interface PluginEntry {
    /**
     * Initialize the plugin. Runs in a background thread.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Provide the plugin's Koin module.
     */
    fun getKoinModule(): Module

    /**
     * Provide the worker instance.
     */
    fun getProcessor(): DataProcessor

    /**
     * Get the plugin manifest information.
     */
    fun getManifest(): PluginManifest

    /**
     * Set the debug mode for the plugin.
     * @param isDebug True if the application is running in debug mode.
     */
    fun setDebug(isDebug: Boolean)

    /**
     * Clean up resources.
     */
    fun shutdown()
}

/**
 * The actual processor performing the business logic.
 */
interface DataProcessor {
    /**
     * Process data natively without serialization.
     * @param request Native request object.
     * @return Result wrapping native response.
     */
    suspend fun process(request: PluginRequest): Result<PluginResponse>

    /**
     * Set the debug mode for the processor.
     */
    fun setDebug(isDebug: Boolean) {}

    /**
     * Observe processing progress (0.0 to 1.0).
     */
    fun observeProgress(): Flow<Float>? = null
}
