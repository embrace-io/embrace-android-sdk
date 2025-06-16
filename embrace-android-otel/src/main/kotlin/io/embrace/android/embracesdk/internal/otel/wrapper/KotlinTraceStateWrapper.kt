package io.embrace.android.embracesdk.internal.otel.wrapper

import android.os.Build
import io.embrace.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.api.trace.TraceStateBuilder
import java.util.function.BiConsumer

class KotlinTraceStateWrapper(
    private val impl: TraceState,
) : io.opentelemetry.api.trace.TraceState {

    override fun get(key: String): String? = impl.get(key)

    override fun size(): Int = impl.asMap().size

    override fun isEmpty(): Boolean = impl.asMap().isEmpty()

    override fun forEach(consumer: BiConsumer<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            impl.asMap().forEach(consumer)
        }
    }

    override fun asMap(): MutableMap<String, String> = impl.asMap().toMutableMap()

    override fun toBuilder(): TraceStateBuilder {
        val builder = io.opentelemetry.api.trace.TraceState.builder()
        asMap().forEach { entry ->
            builder.put(entry.key, entry.value)
        }
        return builder
    }
}
