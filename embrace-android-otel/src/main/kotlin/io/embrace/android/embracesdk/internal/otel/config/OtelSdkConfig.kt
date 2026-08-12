package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.EmbraceLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.ConcurrentHashMap

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
    private val eventMetadataProvider: () -> Map<String, String> = { emptyMap() },
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId,
    private val externalExportDispatcher: ExternalExportDispatcher = ExternalExportDispatcher(),
) {

    private val customAttributes: MutableMap<String, String> = ConcurrentHashMap()

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

            customAttributes.forEach {
                setStringAttribute(it.key, it.value)
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

    private val externalSpanExporters = mutableListOf<SpanExporter>()
    private val externalSpanProcessors = mutableListOf<SpanProcessor>()
    private val externalLogExporters = mutableListOf<LogRecordExporter>()
    private val externalLogRecordProcessors = mutableListOf<LogRecordProcessor>()

    private var exportEnabled: Boolean = true
    private val exportCheck: () -> Boolean = { exportEnabled }

    fun disableDataExport() {
        exportEnabled = false
    }

    fun shutdownExport() {
        externalExportDispatcher.shutdown()
    }

    private val spanExporter: DefaultSpanExporter by lazy {
        DefaultSpanExporter(
            spanRepository = spanRepository,
            externalExporters = externalSpanExporters.toList(),
            exportCheck = exportCheck,
            externalExportDispatcher = externalExportDispatcher,
        )
    }
    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            sessionIdsProvider,
            userIdProvider,
            processIdentifier,
            spanExporter,
        )
    }

    private val logRecordExporter: DefaultLogRecordExporter by lazy {
        DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = externalLogExporters.toList(),
            exportCheck = exportCheck,
            externalExportDispatcher = externalExportDispatcher,
        )
    }

    val logRecordProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(
            uuidSource,
            eventMetadataProvider,
            logRecordExporter,
        )
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    fun addSpanProcessor(spanProcessor: SpanProcessor) {
        externalSpanProcessors.add(spanProcessor)
    }

    fun getExternalSpanProcessors(): List<SpanProcessor> = externalSpanProcessors.toList()

    fun addLogExporter(logExporter: LogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        externalLogRecordProcessors.add(logRecordProcessor)
    }

    fun getExternalLogRecordProcessors(): List<LogRecordProcessor> = externalLogRecordProcessors.toList()

    fun hasConfiguredOtlpExport(): Boolean = externalLogExporters.isNotEmpty() || externalLogRecordProcessors.isNotEmpty() ||
        externalSpanExporters.isNotEmpty() || externalSpanProcessors.isNotEmpty()

    fun setResourceAttribute(key: String, value: String) {
        customAttributes[key] = value
    }
}
