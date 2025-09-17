package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.semconv.AndroidAttributes
import io.embrace.opentelemetry.kotlin.semconv.DeviceAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.OsAttributes
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
import io.embrace.opentelemetry.kotlin.semconv.TelemetryAttributes
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.ConcurrentHashMap

/**
 * Globally referenceable flag that determines which OTel SDK implementation is in use for the production SDK at runtime.
 */
const val DEFAULT_USE_KOTLIN_SDK: Boolean = false

@OptIn(ExperimentalApi::class)
class OtelSdkConfig(
    spanSink: SpanSink,
    logSink: LogSink,
    val sdkName: String,
    val sdkVersion: String,
    private val systemInfo: SystemInfo,
    private val sessionIdProvider: () -> String? = { null },
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId
) {

    private val customAttributes: MutableMap<String, String> = ConcurrentHashMap()

    @OptIn(IncubatingApi::class)
    val resourceAction: MutableAttributeContainer.() -> Unit
        get() = {
            setStringAttribute(ServiceAttributes.SERVICE_NAME, sdkName)
            setStringAttribute(ServiceAttributes.SERVICE_VERSION, sdkVersion)
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
        EmbTrace.trace("process-identifier-init", processIdentifierProvider)
    }

    private val externalSpanExporters = mutableListOf<SpanExporter>()
    private val externalLogExporters = mutableListOf<LogRecordExporter>()

    private var exportEnabled: Boolean = true
    private val exportCheck: () -> Boolean = { exportEnabled }

    fun disableDataExport() {
        exportEnabled = false
    }

    val spanExporter: SpanExporter by lazy {
        DefaultSpanExporter(
            spanSink = spanSink,
            externalExporters = externalSpanExporters.toList(),
            exportCheck = exportCheck,
        )
    }
    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            sessionIdProvider,
            processIdentifier
        )
    }

    val logRecordExporter: LogRecordExporter by lazy {
        DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = externalLogExporters.toList(),
            exportCheck = exportCheck,
        )
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    fun addLogExporter(logExporter: LogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    fun hasConfiguredOtelExporters(): Boolean = externalLogExporters.isNotEmpty() || externalSpanExporters.isNotEmpty()

    fun setResourceAttribute(key: String, value: String) {
        customAttributes[key] = value
    }
}
