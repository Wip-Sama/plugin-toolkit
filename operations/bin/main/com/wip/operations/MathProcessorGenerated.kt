package com.wip.operations

import com.wip.plugin.api.Capability
import com.wip.plugin.api.DataProcessor
import com.wip.plugin.api.ModuleInfo
import com.wip.plugin.api.ParameterMetadata
import com.wip.plugin.api.PluginEntry
import com.wip.plugin.api.PluginManifest
import com.wip.plugin.api.PluginRequest
import com.wip.plugin.api.PluginResponse
import com.wip.plugin.api.Requirements
import com.wip.plugin.api.getDataType
import kotlin.Boolean
import kotlin.Double
import kotlin.Result
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.core.module.Module

public object MathProcessorManifest {
  public val manifest: PluginManifest = PluginManifest(
        manifestVersion = "1.0",
        module = ModuleInfo(id = "com.wip.operations.math", name = "Math Operations", version = "1.2.0", description = "A module that provides mathematical operations on lists of numbers."),
        requirements = Requirements(minMemoryMb = 128, minExecutionTimeMs = 10),
        capabilities = listOf(
          Capability(
            name = "sum",
            description = "Calculates the sum of a list of numbers",
            parameters = mapOf(
              "values" to ParameterMetadata(null, "List of numbers to add", getDataType<List<Double>>())
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "multiply",
            description = "Calculates the product of a list of numbers",
            parameters = mapOf(
              "values" to ParameterMetadata(null, "List of numbers to multiply", getDataType<List<Double>>())
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "subtract",
            description = "Subtracts numbers sequentially",
            parameters = mapOf(
              "a" to ParameterMetadata(null, "Numerator", getDataType<Double>()),
              "b" to ParameterMetadata(null, "Denominator", getDataType<Double>())
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "divide",
            description = "Divides two numbers",
            parameters = mapOf(
              "a" to ParameterMetadata(null, "Numerator", getDataType<Double>()),
              "b" to ParameterMetadata(null, "Denominator", getDataType<Double>())
            ),
            returnType = getDataType<Double>()
          )
        )
      )
}

public class MathProcessorDispatcher(
  private val processor: MathProcessor,
) : DataProcessor {
  private var isDebug: Boolean = false

  private val handlers: Map<String, suspend (PluginRequest) -> PluginResponse> = mapOf(
        "sum" to { request ->
          val result = processor.sumCapability(
            Json.decodeFromJsonElement<List<Double>>(request.parameters["values"] ?: throw IllegalArgumentException("Missing mandatory parameter: values"))
          )
          PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success"))
        },
        "multiply" to { request ->
          val result = processor.multiplyCapability(
            Json.decodeFromJsonElement<List<Double>>(request.parameters["values"] ?: throw IllegalArgumentException("Missing mandatory parameter: values"))
          )
          PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success"))
        },
        "subtract" to { request ->
          val result = processor.subtractCapability(
            Json.decodeFromJsonElement<Double>(request.parameters["a"] ?: throw IllegalArgumentException("Missing mandatory parameter: a")),
            Json.decodeFromJsonElement<Double>(request.parameters["b"] ?: throw IllegalArgumentException("Missing mandatory parameter: b"))
          )
          PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success"))
        },
        "divide" to { request ->
          val result = processor.divideCapability(
            Json.decodeFromJsonElement<Double>(request.parameters["a"] ?: throw IllegalArgumentException("Missing mandatory parameter: a")),
            Json.decodeFromJsonElement<Double>(request.parameters["b"] ?: throw IllegalArgumentException("Missing mandatory parameter: b"))
          )
          PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success"))
        }
      )

  override fun setDebug(isDebug: Boolean) {
    this.isDebug = isDebug
  }

  override suspend fun process(request: PluginRequest): Result<PluginResponse> = try {
    val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException("Unknown method: ${'$'}{request.method}")
    Result.success(handler(request))
  } catch (e: Exception) {
    Result.failure(e)
  }
}

public class MathProcessorPluginEntry(
  private val processor: MathProcessor = MathProcessor(),
) : PluginEntry {
  private val dispatcher: MathProcessorDispatcher = MathProcessorDispatcher(processor)

  private var isDebug: Boolean = false

  override suspend fun initialize(): Result<Unit> = Result.success(Unit)

  override fun getKoinModule(): Module = org.koin.dsl.module {
    single { processor }
    single<PluginEntry> { this@MathProcessorPluginEntry }
  }

  override fun getProcessor(): DataProcessor = dispatcher

  override fun getManifest(): PluginManifest = MathProcessorManifest.manifest

  override fun setDebug(isDebug: Boolean) {
    this.isDebug = isDebug
    dispatcher.setDebug(isDebug)
  }

  override fun shutdown() {
  }
}
