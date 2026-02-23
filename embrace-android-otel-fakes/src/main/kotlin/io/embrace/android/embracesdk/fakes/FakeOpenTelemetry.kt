package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry

/**
 * Creates a instance of [OpenTelemetry] that can be used in tests
 */
@OptIn(ExperimentalApi::class)
fun fakeOpenTelemetry(useKotlinSdk: Boolean = true): OpenTelemetry = if (useKotlinSdk) {
    createOpenTelemetry()
} else {
    createCompatOpenTelemetry()
}
