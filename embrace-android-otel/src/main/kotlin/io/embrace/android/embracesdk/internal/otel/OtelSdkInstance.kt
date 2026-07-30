package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ImplicitContextStorageMode
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.init.LoggerProviderConfigDsl
import io.opentelemetry.kotlin.init.TracerProviderConfigDsl

internal fun createSdkOtelInstance(
    useKotlinSdk: Boolean,
    tracerProvider: TracerProviderConfigDsl.() -> Unit = {},
    loggerProvider: LoggerProviderConfigDsl.() -> Unit = {},
    clock: Clock,
): OpenTelemetry {
    return if (useKotlinSdk) {
        createOpenTelemetry(clock) {
            // opentelemetry-kotlin stores implicit context in a process-wide slot, whereas we want
            // to match opentelemetry-java's default behavior
            context { storageMode = ImplicitContextStorageMode.THREAD_LOCAL }
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
        }
    } else {
        createCompatOpenTelemetry(clock) {
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
        }
    }
}

internal fun OpenTelemetry.getDefaultContext(): Context? {
    return context.implicit().getEmbraceSpan(this)?.createContext(this)
}
