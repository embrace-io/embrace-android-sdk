package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OTelApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : OTelApi {

    private val logger = bootstrapper.logger

    override fun addSpanExporter(spanExporter: SpanExporter) {
        if (sdkCallChecker.started.get()) {
            logger.logError("A SpanExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addSpanExporter(spanExporter)
    }

    override fun getOpenTelemetry(): OpenTelemetry {
        return if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.externalOpenTelemetry
        } else {
            OpenTelemetry.noop()
        }
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        if (sdkCallChecker.started.get()) {
            logger.logError("A LogRecordExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addLogExporter(logRecordExporter)
    }
}
