package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.init.LoggerProviderConfigDsl
import io.opentelemetry.kotlin.init.TracerProviderConfigDsl

@OptIn(ExperimentalApi::class)
internal fun createSdkOtelInstance(
    useKotlinSdk: Boolean,
    tracerProvider: TracerProviderConfigDsl.() -> Unit = {},
    loggerProvider: LoggerProviderConfigDsl.() -> Unit = {},
    clock: Clock,
): OpenTelemetry {
    return if (useKotlinSdk) {
        createOpenTelemetry(clock) {
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

@OptIn(ExperimentalApi::class)
internal fun OpenTelemetry.getDefaultContext(useKotlinSdk: Boolean): Context? {
    return if (useKotlinSdk) {
        contextFactory.root().getEmbraceSpan(this)?.createContext(this)
    } else {
        contextFactory.implicitContext().getEmbraceSpan(this)?.createContext(this)
    }
}
