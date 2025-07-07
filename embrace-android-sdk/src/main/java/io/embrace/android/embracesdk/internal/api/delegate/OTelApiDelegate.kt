package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter

internal class OTelApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : OTelApi {

    override fun addSpanExporter(spanExporter: OtelJavaSpanExporter) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.addSpanExporter(spanExporter)
    }

    override fun addLogRecordExporter(logRecordExporter: OtelJavaLogRecordExporter) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.addLogExporter(logRecordExporter)
    }

    override fun getOpenTelemetry(): OtelJavaOpenTelemetry {
        return if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.externalOpenTelemetry
        } else {
            OtelJavaOpenTelemetry.noop()
        }
    }

    override fun setResourceAttribute(key: String, value: String) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.setResourceAttribute(key, value)
    }
}
