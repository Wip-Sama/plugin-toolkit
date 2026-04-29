package com.wip.slicer

import com.wip.plugin.api.Capability
import com.wip.plugin.api.DataProcessor
import com.wip.plugin.api.ExecutionContext
import com.wip.plugin.api.ModuleInfo
import com.wip.plugin.api.ParameterMetadata
import com.wip.plugin.api.PluginEntry
import com.wip.plugin.api.PluginManifest
import com.wip.plugin.api.PluginRequest
import com.wip.plugin.api.PluginResponse
import com.wip.plugin.api.Requirements
import com.wip.plugin.api.getDataType
import kotlin.Boolean
import kotlin.Int
import kotlin.Result
import kotlin.String
import kotlin.Unit
import kotlin.collections.Map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.core.module.Module

public object SlicerManifest {
  public val manifest: PluginManifest = PluginManifest(
        manifestVersion = "1.0",
        module = ModuleInfo(id = "com.wip.operations.slicer", name = "Slicer", version = "0.4.2", description = "A module that provides vertical images sliding capabilities for manhwa."),
        requirements = Requirements(minMemoryMb = 128, minExecutionTimeMs = 10),
        capabilities = listOf(
          Capability(
            name = "slicer",
            description = "Slices a list of images",
            parameters = mapOf(
              "folderPath" to ParameterMetadata(null, "List of items to slice", getDataType<String>()),
              "minHeight" to ParameterMetadata(Json.parseToJsonElement("1000"), "Minimum height", getDataType<Int>()),
              "desiredHeight" to ParameterMetadata(Json.parseToJsonElement("10000"), "Desired Height", getDataType<Int>()),
              "maxHeight" to ParameterMetadata(Json.parseToJsonElement("10000"), "Maximum Height", getDataType<Int>()),
              "prioritizeSmallerImages" to ParameterMetadata(Json.parseToJsonElement("true"), "Prioritize smaller", getDataType<Boolean>()),
              "cutTolerance" to ParameterMetadata(Json.parseToJsonElement("5"), "Cut tolerance", getDataType<Int>()),
            ),
            returnType = getDataType<String>()
          )
        )
      )
}

public class SlicerDispatcher(
  private val processor: Slicer,
) : DataProcessor {
  private var context: ExecutionContext? = null

  private var isDebug: Boolean = false

  private val handlers: Map<String, suspend (PluginRequest) -> PluginResponse> = mapOf(
        "slicer" to { request ->
          val result = processor.slicer(
            Json.decodeFromJsonElement<String>(request.parameters["folderPath"] ?: throw IllegalArgumentException("Missing mandatory parameter: folderPath")),
            Json.decodeFromJsonElement<Int>(request.parameters["minHeight"] ?: throw IllegalArgumentException("Missing mandatory parameter: minHeight")),
            Json.decodeFromJsonElement<Int>(request.parameters["desiredHeight"] ?: throw IllegalArgumentException("Missing mandatory parameter: desiredHeight")),
            Json.decodeFromJsonElement<Int>(request.parameters["maxHeight"] ?: throw IllegalArgumentException("Missing mandatory parameter: maxHeight")),
            Json.decodeFromJsonElement<Boolean>(request.parameters["prioritizeSmallerImages"] ?: throw IllegalArgumentException("Missing mandatory parameter: prioritizeSmallerImages")),
            Json.decodeFromJsonElement<Int>(request.parameters["cutTolerance"] ?: throw IllegalArgumentException("Missing mandatory parameter: cutTolerance")),
            context?.logger ?: throw IllegalStateException("Logger not available"),
            context?.progress ?: throw IllegalStateException("Progress reporter not available")
          )
          PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success"))
        }
      )

  override fun setDebug(isDebug: Boolean) {
    this.isDebug = isDebug
  }

  override fun setExecutionContext(context: ExecutionContext) {
    this.context = context
  }

  override suspend fun process(request: PluginRequest): Result<PluginResponse> = try {
    val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException("Unknown method: ${'$'}{request.method}")
    Result.success(handler(request))
  } catch (e: Exception) {
    Result.failure(e)
  }
}

public class SlicerPluginEntry(
  private val processor: Slicer = Slicer(),
) : PluginEntry {
  private val dispatcher: SlicerDispatcher = SlicerDispatcher(processor)

  private var isDebug: Boolean = false

  override suspend fun initialize(): Result<Unit> = Result.success(Unit)

  override fun getKoinModule(): Module = org.koin.dsl.module {
    single { processor }
    single<PluginEntry> { this@SlicerPluginEntry }
  }

  override fun getProcessor(): DataProcessor = dispatcher

  override fun getManifest(): PluginManifest = SlicerManifest.manifest

  override fun setDebug(isDebug: Boolean) {
    this.isDebug = isDebug
    dispatcher.setDebug(isDebug)
  }

  override fun shutdown() {
  }
}
