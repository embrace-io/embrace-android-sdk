package io.embrace.android.embracesdk.internal.otel
import io.embrace.android.embracesdk.internal.otel.config.USE_KOTLIN_SDK
import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.createOpenTelemetryKotlin
import io.embrace.opentelemetry.kotlin.creator.ObjectCreator
import io.embrace.opentelemetry.kotlin.creator.current
import io.embrace.opentelemetry.kotlin.default
import io.embrace.opentelemetry.kotlin.init.LoggerProviderConfigDsl
import io.embrace.opentelemetry.kotlin.init.TracerProviderConfigDsl

@OptIn(ExperimentalApi::class)
internal fun OpenTelemetryInstance.get(
    useKotlinSdk: Boolean = USE_KOTLIN_SDK,
    tracerProvider: TracerProviderConfigDsl.() -> Unit = {},
    loggerProvider: LoggerProviderConfigDsl.() -> Unit = {},
    clock: Clock
): OpenTelemetry {
    return if (useKotlinSdk) {
        default(
            tracerProvider = tracerProvider,
            loggerProvider = loggerProvider,
            clock = clock,
        )
    } else {
        createOpenTelemetryKotlin(
            tracerProvider = tracerProvider,
            loggerProvider = loggerProvider,
            clock = clock
        )
    }
}

@OptIn(ExperimentalApi::class)
internal fun ObjectCreator.getDefaultContext(): Context? {
    return if (USE_KOTLIN_SDK) {
        context.root().getEmbraceSpan(this)?.createContext(this)
    } else {
        context.current().getEmbraceSpan(this)?.createContext(this)
    }
}
