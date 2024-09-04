package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.IdGenerator
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.Systrace
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
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes

public class OpenTelemetryConfiguration(
    spanSink: SpanSink,
    logSink: LogSink,
    systemInfo: SystemInfo
) {
    public val embraceSdkName: String = BuildConfig.LIBRARY_PACKAGE_NAME
    public val embraceSdkVersion: String = BuildConfig.VERSION_NAME
    public val resource: Resource = Resource.getDefault().toBuilder()
        .put(ServiceAttributes.SERVICE_NAME, embraceSdkName)
        .put(ServiceAttributes.SERVICE_VERSION, embraceSdkVersion)
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

    /**
     * Unique ID generated for an instance of the app process and not related to the actual process ID assigned by the OS.
     * This allows us to explicitly relate all the sessions associated with a particular app launch rather than having the backend figure
     * this out by proximity for stitched sessions.
     */
    private val processIdentifier: String by lazy {
        Systrace.traceSynchronous("process-identifier-init", IdGenerator.Companion::generateLaunchInstanceId)
    }

    private val externalSpanExporters = mutableListOf<SpanExporter>()
    private val externalLogExporters = mutableListOf<LogRecordExporter>()

    public val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            EmbraceSpanExporter(
                spanSink = spanSink,
                externalSpanExporter = SpanExporter.composite(externalSpanExporters)
            ),
            processIdentifier
        )
    }

    public val logProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(
            EmbraceLogRecordExporter(
                logSink = logSink,
                externalLogRecordExporter = LogRecordExporter.composite(externalLogExporters)
            )
        )
    }

    public fun addSpanExporter(spanExporter: SpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    public fun addLogExporter(logExporter: LogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    public fun hasConfiguredOtelExporters(): Boolean = externalLogExporters.isNotEmpty() || externalSpanExporters.isNotEmpty()
}
