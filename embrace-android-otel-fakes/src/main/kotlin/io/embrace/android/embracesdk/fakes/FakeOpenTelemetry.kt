package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.createOtelJavaCurrentContextStorage
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry

/**
 * Creates a instance of [OpenTelemetry] that can be used in tests
 */
fun fakeOpenTelemetry(otelSdkMode: OtelSdkMode): OpenTelemetry = if (otelSdkMode.useKotlinSdk) {
    createOpenTelemetry {
        context { storage { createOtelJavaCurrentContextStorage() } }
    }
} else {
    createCompatOpenTelemetry()
}
