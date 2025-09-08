package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.noop
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter

@OptIn(ExperimentalApi::class)
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

    override fun setResourceAttribute(key: String, value: String) {
        if (sdkCallChecker.started.get()) {
            return
        }
        bootstrapper.openTelemetryModule.otelSdkConfig.setResourceAttribute(key, value)
    }

    @ExperimentalApi
    override fun getOpenTelemetryKotlin(): OpenTelemetry {
        return if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
        } else {
            OpenTelemetryInstance.noop()
        }
    }
}
