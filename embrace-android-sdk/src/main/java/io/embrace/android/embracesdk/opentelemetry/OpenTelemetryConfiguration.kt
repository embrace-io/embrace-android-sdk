package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordExporter
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordProcessor
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes

internal class OpenTelemetryConfiguration(
    spanSink: SpanSink,
    logSink: LogSink,
    systemInfo: SystemInfo,
    processIdentifier: String
) {
    val embraceSdkName = BuildConfig.LIBRARY_PACKAGE_NAME
    val embraceSdkVersion = BuildConfig.VERSION_NAME
    val resource: Resource = Resource.getDefault().toBuilder()
        .put(ServiceIncubatingAttributes.SERVICE_NAME, embraceSdkName)
        .put(ServiceIncubatingAttributes.SERVICE_VERSION, embraceSdkVersion)
        .put(OsIncubatingAttributes.OS_NAME, systemInfo.osName)
        .put(OsIncubatingAttributes.OS_VERSION, systemInfo.osVersion)
        .put(OsIncubatingAttributes.OS_TYPE, systemInfo.osType)
        .put(OsIncubatingAttributes.OS_BUILD_ID, systemInfo.osBuild)
        .put(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL, systemInfo.androidOsApiLevel)
        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, systemInfo.deviceManufacturer)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, systemInfo.deviceModel)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, systemInfo.deviceModel)
        .put(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME, embraceSdkName)
        .put(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION, embraceSdkVersion)
        .build()

    private val externalSpanExporters = mutableListOf<SpanExporter>()
    private val externalLogExporters = mutableListOf<LogRecordExporter>()

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            EmbraceSpanExporter(
                spanSink = spanSink,
                externalSpanExporter = SpanExporter.composite(externalSpanExporters)
            ),
            processIdentifier
        )
    }

    val logProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(
            EmbraceLogRecordExporter(
                logSink = logSink,
                externalLogRecordExporter = LogRecordExporter.composite(externalLogExporters)
            )
        )
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    fun addLogExporter(logExporter: LogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    fun hasConfiguredOtelExporters() = externalLogExporters.isNotEmpty() || externalSpanExporters.isNotEmpty()
}
