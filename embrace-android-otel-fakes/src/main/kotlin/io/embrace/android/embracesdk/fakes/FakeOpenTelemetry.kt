package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.createCompatOpenTelemetry
import io.embrace.opentelemetry.kotlin.createOpenTelemetry

/**
 * Creates a instance of [OpenTelemetry] that can be used in tests
 */
@OptIn(ExperimentalApi::class)
fun fakeOpenTelemetry(useKotlinSdk: Boolean = true): OpenTelemetry = if (useKotlinSdk) {
    createOpenTelemetry()
} else {
    createCompatOpenTelemetry()
}
