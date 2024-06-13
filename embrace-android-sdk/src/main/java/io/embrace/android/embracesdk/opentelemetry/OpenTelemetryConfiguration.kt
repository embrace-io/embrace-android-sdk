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

internal class OpenTelemetryConfiguration(
    spanSink: SpanSink,
    logSink: LogSink,
    systemInfo: SystemInfo,
    processIdentifier: String
) {
    val embraceSdkName = BuildConfig.LIBRARY_PACKAGE_NAME
    val embraceSdkVersion = BuildConfig.VERSION_NAME
    val resource: Resource = Resource.getDefault().toBuilder()
        .put(serviceName, embraceSdkName)
        .put(serviceVersion, embraceSdkVersion)
        .put(osName, systemInfo.osName)
        .put(osVersion, systemInfo.osVersion)
        .put(osType, systemInfo.osType)
        .put(osBuildId, systemInfo.osBuild)
        .put(androidApiLevel, systemInfo.androidOsApiLevel)
        .put(deviceManufacturer, systemInfo.deviceManufacturer)
        .put(deviceModelIdentifier, systemInfo.deviceModel)
        .put(deviceModelName, systemInfo.deviceModel)
        .put(telemetryDistroName, embraceSdkName)
        .put(telemetryDistroVersion, embraceSdkVersion)
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
