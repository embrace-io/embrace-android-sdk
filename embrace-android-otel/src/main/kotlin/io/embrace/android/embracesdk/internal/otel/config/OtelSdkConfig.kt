package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.EmbraceLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.EmbraceLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordProcessor
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResource
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResourceBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes

class OtelSdkConfig(
    spanSink: SpanSink,
    logSink: LogSink,
    val sdkName: String,
    val sdkVersion: String,
    systemInfo: SystemInfo,
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId,
) {
    val resourceBuilder: OtelJavaResourceBuilder = OtelJavaResource.getDefault().toBuilder()
        .put(ServiceAttributes.SERVICE_NAME, sdkName)
        .put(ServiceAttributes.SERVICE_VERSION, sdkVersion)
        .put(OsIncubatingAttributes.OS_NAME, systemInfo.osName)
        .put(OsIncubatingAttributes.OS_VERSION, systemInfo.osVersion)
        .put(OsIncubatingAttributes.OS_TYPE, systemInfo.osType)
        .put(OsIncubatingAttributes.OS_BUILD_ID, systemInfo.osBuild)
        .put(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL, systemInfo.androidOsApiLevel)
        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, systemInfo.deviceManufacturer)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, systemInfo.deviceModel)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, systemInfo.deviceModel)
        .put(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME, sdkName)
        .put(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION, sdkVersion)

    /**
     * Unique ID generated for an instance of the app process and not related to the actual process ID assigned by the OS.
     * This allows us to explicitly relate all the sessions associated with a particular app launch rather than having the backend figure
     * this out by proximity for stitched sessions.
     */
    val processIdentifier: String by lazy {
        EmbTrace.trace("process-identifier-init", processIdentifierProvider)
    }

    private val externalSpanExporters = mutableListOf<OtelJavaSpanExporter>()
    private val externalLogExporters = mutableListOf<OtelJavaLogRecordExporter>()

    private var exportEnabled: Boolean = true
    private val exportCheck: () -> Boolean = { exportEnabled }

    fun disableDataExport() {
        exportEnabled = false
    }

    val spanProcessor: OtelJavaSpanProcessor by lazy {
        EmbraceSpanProcessor(
            EmbraceSpanExporter(
                spanSink = spanSink,
                externalSpanExporter = if (externalSpanExporters.isNotEmpty()) {
                    OtelJavaSpanExporter.composite(externalSpanExporters)
                } else {
                    null
                },
                exportCheck = exportCheck,
            ),
            processIdentifier
        )
    }

    val logProcessor: OtelJavaLogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(
            EmbraceLogRecordExporter(
                logSink = logSink,
                externalLogRecordExporter = if (externalLogExporters.isNotEmpty()) {
                    OtelJavaLogRecordExporter.composite(externalLogExporters)
                } else {
                    null
                },
                exportCheck = exportCheck,
            )
        )
    }

    fun addSpanExporter(spanExporter: OtelJavaSpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    fun addLogExporter(logExporter: OtelJavaLogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    fun hasConfiguredOtelExporters(): Boolean = externalLogExporters.isNotEmpty() || externalSpanExporters.isNotEmpty()
}
