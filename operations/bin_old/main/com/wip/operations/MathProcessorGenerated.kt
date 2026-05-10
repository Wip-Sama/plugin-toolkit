package com.wip.operations

import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.Result
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.core.module.Module
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataProcessor
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.api.SettingMetadata
import org.wip.plugintoolkit.api.getDataType

public object MathProcessorManifest {
  public val manifest: PluginManifest = PluginManifest(
        manifestVersion = "1.0",
        plugin = PluginInfo(id = "com.wip.operations.math", name = "Math Operations", version = "1.3.6", description = "A module that provides mathematical operations on lists of numbers."),
        requirements = Requirements(minMemoryMb = 128, minExecutionTimeMs = 10),
        capabilities = listOf(
          Capability(
            name = "sum",
            description = "Calculates the sum of a list of numbers",
            isPausable = false,
            isCancellable = true,
            parameters = mapOf(
              "values" to ParameterMetadata(defaultValue = null, description = "List of numbers to add", type = getDataType<List<Double>>(), constraints = null, required = false, secret = false)
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "multiply",
            description = "Calculates the product of a list of numbers",
            isPausable = false,
            isCancellable = true,
            parameters = mapOf(
              "values" to ParameterMetadata(defaultValue = null, description = "List of numbers to multiply", type = getDataType<List<Double>>(), constraints = null, required = false, secret = false)
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "subtract",
            description = "Subtracts numbers sequentially",
            isPausable = false,
            isCancellable = true,
            parameters = mapOf(
              "a" to ParameterMetadata(defaultValue = Json.parseToJsonElement("0.0"), description = "Numerator", type = getDataType<Double>(), constraints = null, required = false, secret = false),
              "b" to ParameterMetadata(defaultValue = Json.parseToJsonElement("0.0"), description = "Denominator", type = getDataType<Double>(), constraints = null, required = false, secret = false)
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "divide",
            description = "Divides two numbers",
            isPausable = false,
            isCancellable = true,
            parameters = mapOf(
              "a" to ParameterMetadata(defaultValue = Json.parseToJsonElement("1.0"), description = "Numerator", type = getDataType<Double>(), constraints = null, required = false, secret = false),
              "b" to ParameterMetadata(defaultValue = Json.parseToJsonElement("1.0"), description = "Denominator", type = getDataType<Double>(), constraints = null, required = false, secret = false)
            ),
            returnType = getDataType<Double>()
          ),
          Capability(
            name = "slow_sum",
            description = "Calculates the sum of a list of numbers with a delay",
            isPausable = false,
            isCancellable = true,
            parameters = mapOf(
              "values" to ParameterMetadata(defaultValue = null, description = "List of numbers to add", type = getDataType<List<Double>>(), constraints = null, required = false, secret = false),
              "delay" to ParameterMetadata(defaultValue = Json.parseToJsonElement("1000"), description = "Delay in milliseconds", type = getDataType<Long>(), constraints = null, required = false, secret = false)
            ),
            returnType = getDataType<Double>()
          )
        )
        ,
        actions = listOf(
          PluginAction(name = "Reset Statistics", description = "Resets all internal math counters and history.", functionName = "resetStats"),
          PluginAction(name = "Run Diagnostics", description = "Checks the health of the math engine.", functionName = "runDiagnostics")
        )
        ,
        settings = mapOf(
          "googleApiKey" to SettingMetadata(defaultValue = Json.parseToJsonElement("null"), description = "API Key for Google services", type = getDataType<String>(), required = false, secret = false),
          "serverToken" to SettingMetadata(defaultValue = null, description = "Secure token for the operations server", type = getDataType<String>(), required = true, secret = true),
          "operatorName" to SettingMetadata(defaultValue = null, description = "Name of the person performing the operations", type = getDataType<String>(), required = true, secret = false)
        )
        ,
        hasUpdateHandler = false,
        hasSetupHandler = false
      )
}

public class MathProcessorDispatcher(
  private val processor: MathProcessor,
) : DataProcessor {
  private var context: PluginContext? = null

  private var isDebug: Boolean = false

  private val handlers: Map<String, suspend (PluginRequest) -> ExecutionResult> = mapOf(
        "sum" to { request ->
          val result = processor.sumCapability(
            Json.decodeFromJsonElement<List<Double>>(request.parameters["values"] ?: throw IllegalArgumentException("Missing mandatory parameter: values"))
          )
          ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success")))
        },
        "multiply" to { request ->
          val result = processor.multiplyCapability(
            Json.decodeFromJsonElement<List<Double>>(request.parameters["values"] ?: throw IllegalArgumentException("Missing mandatory parameter: values"))
          )
          ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success")))
        },
        "subtract" to { request ->
          val result = processor.subtractCapability(
            Json.decodeFromJsonElement<Double>(request.parameters["a"] ?: throw IllegalArgumentException("Missing mandatory parameter: a")),
            Json.decodeFromJsonElement<Double>(request.parameters["b"] ?: throw IllegalArgumentException("Missing mandatory parameter: b"))
          )
          ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success")))
        },
        "divide" to { request ->
          val result = processor.divideCapability(
            Json.decodeFromJsonElement<Double>(request.parameters["a"] ?: throw IllegalArgumentException("Missing mandatory parameter: a")),
            Json.decodeFromJsonElement<Double>(request.parameters["b"] ?: throw IllegalArgumentException("Missing mandatory parameter: b"))
          )
          ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success")))
        },
        "slow_sum" to { request ->
          val result = processor.slowSumCapability(
            Json.decodeFromJsonElement<List<Double>>(request.parameters["values"] ?: throw IllegalArgumentException("Missing mandatory parameter: values")),
            Json.decodeFromJsonElement<Long>(request.parameters["delay"] ?: throw IllegalArgumentException("Missing mandatory parameter: delay"))
          )
          ExecutionResult.Success(PluginResponse(result = Json.encodeToJsonElement(result), metadata = mapOf("status" to "success")))
        }
      )

  private val actionHandlers: Map<String, suspend (PluginContext) -> Result<Unit>> = mapOf(
        "resetstats" to { context ->
          runCatching { processor.resetStats(context.logger) }
        },
        "rundiagnostics" to { context ->
          runCatching { processor.runDiagnostics(context.logger, context.progress) }
        }
      )

  override fun setDebug(isDebug: Boolean) {
    this.isDebug = isDebug
  }

  override fun setPluginContext(context: PluginContext) {
    this.context = context
  }

  override suspend fun process(request: PluginRequest): ExecutionResult = try {
    val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException("Unknown method: ${'$'}{request.method}")
    handler(request)
  } catch (e: Exception) {
    ExecutionResult.Error(e.message ?: "Unknown error", e)
  }

  override fun processAsync(request: PluginRequest): JobHandle {
    val handler = handlers[request.method.lowercase()] ?: throw IllegalArgumentException("Unknown method: ${'$'}{request.method}")
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val deferred = scope.async {
      try {
        handler(request)
      } catch (e: Exception) {
        ExecutionResult.Error(e.message ?: "Unknown error", e)
      }
    }

    return object : JobHandle {
      override val result = deferred
      override fun pause() {
        scope.launch { context?.signals?.sendSignal(PluginSignal.PAUSE) }
      }
      override fun cancel(force: Boolean) {
        scope.launch { context?.signals?.sendSignal(PluginSignal.CANCEL) }
        deferred.cancel()
        if (force) {
          scope.cancel()
        }
      }
    }
  }

  override suspend fun runAction(action: PluginAction, context: PluginContext): Result<Unit> {
    val handler = actionHandlers[action.functionName.lowercase()] ?: return Result.failure(IllegalArgumentException("Unknown action: ${action.functionName}"))
    return handler(context)
  }
}

public class MathProcessorPluginEntry(
  private val processor: MathProcessor = MathProcessor(),
) : PluginEntry {
  private val dispatcher: MathProcessorDispatcher = MathProcessorDispatcher(processor)

  private var isDebug: Boolean = false

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

  override suspend fun performLoad(context: PluginContext): Result<Unit> = Result.success(Unit)

  override suspend fun performSetup(context: PluginContext): Result<Unit> = Result.success(Unit)

  override suspend fun validate(context: PluginContext): Result<Unit> = processor.validate(context.logger, context)

  override suspend fun performUpdate(context: PluginContext): Result<Unit> = Result.success(Unit)
}

public object MathProcessorActions {
  public const val RESET_STATISTICS: String = "resetStats"

  public const val RUN_DIAGNOSTICS: String = "runDiagnostics"
}
