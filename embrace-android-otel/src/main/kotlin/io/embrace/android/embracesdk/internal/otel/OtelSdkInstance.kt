package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.createCompatOpenTelemetry
import io.embrace.opentelemetry.kotlin.createOpenTelemetry
import io.embrace.opentelemetry.kotlin.init.LoggerProviderConfigDsl
import io.embrace.opentelemetry.kotlin.init.TracerProviderConfigDsl

@OptIn(ExperimentalApi::class)
internal fun createSdkOtelInstance(
    useKotlinSdk: Boolean,
    tracerProvider: TracerProviderConfigDsl.() -> Unit = {},
    loggerProvider: LoggerProviderConfigDsl.() -> Unit = {},
    clock: Clock,
): OpenTelemetry {
    return if (useKotlinSdk) {
        createOpenTelemetry {
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
            this.clock = clock
        }
    } else {
        createCompatOpenTelemetry {
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
            this.clock = clock
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
