package io.embrace.android.embracesdk.internal.otel

import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.createCompatOpenTelemetry
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.error.NoopSdkErrorHandler
import io.opentelemetry.kotlin.error.SdkErrorHandler
import io.opentelemetry.kotlin.init.LoggerProviderConfigDsl
import io.opentelemetry.kotlin.init.TracerProviderConfigDsl
import io.opentelemetry.kotlin.init.useOtelJavaContextStorage

internal fun createSdkOtelInstance(
    useKotlinSdk: Boolean,
    tracerProvider: TracerProviderConfigDsl.() -> Unit = {},
    loggerProvider: LoggerProviderConfigDsl.() -> Unit = {},
    clock: Clock,
    errorHandler: SdkErrorHandler = NoopSdkErrorHandler,
): OpenTelemetry {
    return if (useKotlinSdk) {
        createOpenTelemetry(clock) {
            // share implicit context with opentelemetry-java so getJavaOpenTelemetry() and customer OTel Java code interoperate
            context { useOtelJavaContextStorage() }
            errorHandler(errorHandler)
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
        }
    } else {
        createCompatOpenTelemetry(clock) {
            errorHandler(errorHandler)
            tracerProvider { tracerProvider() }
            loggerProvider { loggerProvider() }
        }
    }
}

internal fun OpenTelemetry.getDefaultContext(): Context? {
    return context.implicit().getEmbraceSpan(this)?.createContext(this)
}
