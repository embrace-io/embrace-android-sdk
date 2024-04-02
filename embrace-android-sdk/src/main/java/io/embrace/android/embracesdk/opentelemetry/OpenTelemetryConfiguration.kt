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
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes

internal class OpenTelemetryConfiguration(
    spanSink: SpanSink,
    logSink: LogSink,
    systemInfo: SystemInfo
) {
    val serviceName = BuildConfig.LIBRARY_PACKAGE_NAME
    val serviceVersion = BuildConfig.VERSION_NAME
    val resource: Resource = Resource.getDefault().toBuilder()
        .put(ServiceIncubatingAttributes.SERVICE_NAME, serviceName)
        .put(ServiceIncubatingAttributes.SERVICE_VERSION, serviceVersion)
        .put(OsIncubatingAttributes.OS_NAME, systemInfo.osName)
        .put(OsIncubatingAttributes.OS_DESCRIPTION, systemInfo.osVersionName)
        .put(OsIncubatingAttributes.OS_TYPE, systemInfo.osType)
        .put(OsIncubatingAttributes.OS_VERSION, systemInfo.osVersion)
        .put(OsIncubatingAttributes.OS_BUILD_ID, systemInfo.osBuild)
        .put(DeviceIncubatingAttributes.DEVICE_MANUFACTURER, systemInfo.deviceManufacturer)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER, systemInfo.deviceModel)
        .put(DeviceIncubatingAttributes.DEVICE_MODEL_NAME, systemInfo.deviceModel)
        .build()

    private val spanExporters = mutableListOf<SpanExporter>(EmbraceSpanExporter(spanSink))
    private val logExporters = mutableListOf<LogRecordExporter>(EmbraceLogRecordExporter(logSink))

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(SpanExporter.composite(spanExporters))
    }

    val logProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(LogRecordExporter.composite(logExporters))
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        spanExporters.add(spanExporter)
    }

    fun addLogExporter(logExporter: LogRecordExporter) {
        logExporters.add(logExporter)
    }
}
