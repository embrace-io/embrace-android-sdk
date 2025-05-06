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

    override fun addSpanExporter(spanExporter: SpanExporter) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.addSpanExporter(spanExporter)
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.addLogExporter(logRecordExporter)
    }

    override fun getOpenTelemetry(): OpenTelemetry {
        return if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.externalOpenTelemetry
        } else {
            OpenTelemetry.noop()
        }
    }

    override fun setResourceAttribute(key: String, value: String) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.resourceBuilder.put(key, value)
    }
}
