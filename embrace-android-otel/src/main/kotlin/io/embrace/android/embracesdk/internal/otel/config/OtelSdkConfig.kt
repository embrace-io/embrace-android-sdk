package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.EmbraceLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.PrivateTelemetryFilteringLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.PrivateTelemetryFilteringLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.PrivateTelemetryFilteringSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.PrivateTelemetryFilteringSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.state.SpanStateLink
import io.embrace.android.embracesdk.internal.otel.state.SpanStateReleaseProcessor
import io.embrace.android.embracesdk.internal.otel.state.SpanStateTracker
import io.embrace.android.embracesdk.internal.otel.state.StateAssociation
import io.embrace.android.embracesdk.internal.otel.state.StateDecoratingSpanProcessor
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

class OtelSdkConfig(
    spanRepository: SpanRepository,
    logSink: LogSink,
    val sdkName: String,
    val sdkVersion: String,
    val appVersion: String,
    val packageName: String,
    private val systemInfo: SystemInfo,
    private val uuidSource: UuidSource,
    private val sessionIdsProvider: () -> SessionIdsProvider? = { null },
    private val userIdProvider: () -> String? = { null },
    private val eventMetadataProvider: () -> Map<String, Any> = { emptyMap() },
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId,
    private val externalExportDispatcher: ExternalExportDispatcher,
    val externalConfig: ExternalOtelConfig = ExternalOtelConfig(),
    private val stateAssociation: StateAssociation? = null,
) : AutoCloseable {

    /**
     * Links spans to state changes, where telemetry is associated with state. What it keeps for a span outlives the
     * span's ending only where the values are put on what is exported of it.
     */
    private val spanStateTracker: SpanStateTracker? = stateAssociation?.let {
        SpanStateTracker(it.stateProvider, it.stateAt, keepUntilReleased = it.valuesOnExportedSpans)
    }

    /**
     * What a span the SDK makes tells, and asks of, what links spans to state, where telemetry is associated with state.
     */
    val spanStateLink: SpanStateLink? get() = spanStateTracker

    /**
     * How many spans something is kept for to link them to state.
     */
    internal fun trackedStateSpans(): Int = spanStateTracker?.trackedSpans() ?: 0

    /**
     * The same tracker, where what an app's own exporters and processors are handed of a span carries the values.
     */
    private val spanStateDecorator: SpanStateTracker? = spanStateTracker.takeIf {
        stateAssociation?.valuesOnExportedSpans == true
    }

    val resourceAction: AttributesMutator.() -> Unit
        get() = {
            setStringAttribute(ServiceAttributes.SERVICE_NAME, packageName)
            setStringAttribute(ServiceAttributes.SERVICE_VERSION, appVersion)
            setStringAttribute(OsAttributes.OS_NAME, systemInfo.osName)
            setStringAttribute(OsAttributes.OS_VERSION, systemInfo.osVersion)
            setStringAttribute(OsAttributes.OS_TYPE, systemInfo.osType)
            setStringAttribute(OsAttributes.OS_BUILD_ID, systemInfo.osBuild)
            setStringAttribute(AndroidAttributes.ANDROID_OS_API_LEVEL, systemInfo.androidOsApiLevel)
            setStringAttribute(DeviceAttributes.DEVICE_MANUFACTURER, systemInfo.deviceManufacturer)
            setStringAttribute(DeviceAttributes.DEVICE_MODEL_IDENTIFIER, systemInfo.deviceModel)
            setStringAttribute(DeviceAttributes.DEVICE_MODEL_NAME, systemInfo.deviceModel)
            setStringAttribute(TelemetryAttributes.TELEMETRY_DISTRO_NAME, sdkName)
            setStringAttribute(TelemetryAttributes.TELEMETRY_DISTRO_VERSION, sdkVersion)

            externalConfig.getResourceAttributes().forEach {
                setStringAttribute(it.key, it.value)
            }

            // Says which instance of the process the states in an exported event belong to. This Resource is what an
            // app's own exporters see. Embrace's envelope has a resource of its own, which this is no part of.
            if (stateAssociation?.exportsEvents == true) {
                setStringAttribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, processIdentifier)
            }
        }

    /**
     * Unique ID generated for an instance of the app process and not related to the actual process ID assigned by the OS.
     * This allows us to explicitly relate all the sessions associated with a particular app launch rather than having the backend figure
     * this out by proximity for stitched sessions.
     */
    val processIdentifier: String by lazy {
        EmbTrace.trace(sectionName = "process-identifier-init", code = processIdentifierProvider)
    }

    @Volatile
    private var exportEnabled: Boolean = true
    private val exportCheck: () -> Boolean = { exportEnabled }

    fun disableDataExport() {
        exportEnabled = false
    }

    override fun close() {
        val components = with(externalConfig) {
            getSpanExporters() + getLogExporters() + getSpanProcessors() + getLogRecordProcessors()
        }
        if (components.isNotEmpty()) {
            externalExportDispatcher.dispatch(components) { component ->
                component.shutdown()
            }
        }
        externalExportDispatcher.shutdown()
    }

    private val spanExporter: DefaultSpanExporter by lazy {
        DefaultSpanExporter(
            spanRepository = spanRepository,
            externalExporters = externalConfig.getSpanExporters().map(::PrivateTelemetryFilteringSpanExporter),
            exportCheck = exportCheck,
            externalExportDispatcher = externalExportDispatcher,
            decorate = spanStateDecorator?.let { decorator -> decorator::withValues },
        )
    }
    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            sessionIdsProvider,
            userIdProvider,
            processIdentifier,
            spanExporter,
            spanStateTracker,
        )
    }

    private val logRecordExporter: DefaultLogRecordExporter by lazy {
        DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = externalConfig.getLogExporters().map(::PrivateTelemetryFilteringLogRecordExporter),
            exportCheck = exportCheck,
            externalExportDispatcher = externalExportDispatcher,
        )
    }

    val logRecordProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(
            uuidSource,
            eventMetadataProvider,
            logRecordExporter,
            if (stateAssociation?.exportsEvents == true) logRecordExporter::exportExternally else null,
        )
    }

    val externalSpanProcessors: List<SpanProcessor> by lazy {
        externalConfig.getSpanProcessors().map { processor ->
            val decorated = spanStateDecorator?.let { StateDecoratingSpanProcessor(processor, it) } ?: processor
            PrivateTelemetryFilteringSpanProcessor(decorated, exportCheck)
        }
    }

    /**
     * Processors of the SDK's own that have to see a span after every other processor has.
     */
    val trailingSpanProcessors: List<SpanProcessor> by lazy {
        listOfNotNull(spanStateDecorator?.let(::SpanStateReleaseProcessor))
    }

    val externalLogRecordProcessors: List<LogRecordProcessor> by lazy {
        externalConfig.getLogRecordProcessors().map { PrivateTelemetryFilteringLogRecordProcessor(it, exportCheck) }
    }
}
