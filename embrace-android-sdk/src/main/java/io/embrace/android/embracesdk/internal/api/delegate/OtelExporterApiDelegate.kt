package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.api.OtelExporterApi
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OtelExporterApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : OtelExporterApi {

    private val logger = bootstrapper.logger

    override fun addSpanExporter(spanExporter: SpanExporter) {
        if (sdkCallChecker.started.get()) {
            logger.logError("A SpanExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addSpanExporter(spanExporter)
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        if (sdkCallChecker.started.get()) {
            logger.logError("A LogRecordExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addLogExporter(logRecordExporter)
    }
}
