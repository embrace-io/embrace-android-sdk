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
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.embrace.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanExporter
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Globally referenceable flag that determines which OTel SDK implementation is in use for the production SDK at runtime.
 */
const val USE_KOTLIN_SDK: Boolean = false

@OptIn(ExperimentalApi::class)
class OtelSdkConfig(
    spanSink: SpanSink,
    logSink: LogSink,
    val sdkName: String,
    val sdkVersion: String,
    private val systemInfo: SystemInfo,
    private val sessionIdProvider: () -> String? = { null },
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId,
    val useKotlinSdk: Boolean = USE_KOTLIN_SDK
) {

    private val customAttributes: MutableMap<String, String> = ConcurrentHashMap()

    val resourceAction: MutableAttributeContainer.() -> Unit
        get() = {
            setStringAttribute(ServiceAttributes.SERVICE_NAME.key, sdkName)
            setStringAttribute(ServiceAttributes.SERVICE_VERSION.key, sdkVersion)
            setStringAttribute(OsIncubatingAttributes.OS_NAME.key, systemInfo.osName)
            setStringAttribute(OsIncubatingAttributes.OS_VERSION.key, systemInfo.osVersion)
            setStringAttribute(OsIncubatingAttributes.OS_TYPE.key, systemInfo.osType)
            setStringAttribute(OsIncubatingAttributes.OS_BUILD_ID.key, systemInfo.osBuild)
            setStringAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL.key, systemInfo.androidOsApiLevel)
            setStringAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER.key, systemInfo.deviceManufacturer)
            setStringAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER.key, systemInfo.deviceModel)
            setStringAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME.key, systemInfo.deviceModel)
            setStringAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME.key, sdkName)
            setStringAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION.key, sdkVersion)

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

    fun addSpanExporter(spanExporter: OtelJavaSpanExporter) {
        externalSpanExporters.add(spanExporter.toOtelKotlinSpanExporter())
    }

    fun addLogExporter(logExporter: OtelJavaLogRecordExporter) {
        externalLogExporters.add(logExporter.toOtelKotlinLogRecordExporter())
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
