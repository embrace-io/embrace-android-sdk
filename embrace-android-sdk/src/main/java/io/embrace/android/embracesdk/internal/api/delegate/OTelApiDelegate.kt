package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OTelApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : OTelApi {

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

    override fun getTracer(instrumentationModuleName: String?, instrumentationModuleVersion: String?): Tracer {
        return if (sdkCallChecker.started.get()) {
            if (instrumentationModuleName == null) {
                bootstrapper.openTelemetryModule.sdkTracer
            } else if (instrumentationModuleVersion == null) {
                bootstrapper.openTelemetryModule.externalTracerProvider.get(instrumentationModuleName)
            } else {
                bootstrapper.openTelemetryModule.externalTracerProvider.get(instrumentationModuleName, instrumentationModuleVersion)
            }
        } else {
            TracerProvider.noop().get("")
        }
    }
}
