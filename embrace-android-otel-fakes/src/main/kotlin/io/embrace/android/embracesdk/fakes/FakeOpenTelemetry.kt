package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.init.useOtelJavaContextStorage

/**
 * Creates a instance of [OpenTelemetry] that can be used in tests
 */
fun fakeOpenTelemetry(otelSdkMode: OtelSdkMode): OpenTelemetry = if (otelSdkMode.useKotlinSdk) {
    createOpenTelemetry {
        context { useOtelJavaContextStorage() }
    }
} else {
    createCompatOpenTelemetry()
}
