package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.ImplicitContextStorageMode
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry

/**
 * Creates a instance of [OpenTelemetry] that can be used in tests
 */
fun fakeOpenTelemetry(useKotlinSdk: Boolean = true): OpenTelemetry = if (useKotlinSdk) {
    createOpenTelemetry {
        context { storageMode = ImplicitContextStorageMode.THREAD_LOCAL }
    }
} else {
    createCompatOpenTelemetry()
}
