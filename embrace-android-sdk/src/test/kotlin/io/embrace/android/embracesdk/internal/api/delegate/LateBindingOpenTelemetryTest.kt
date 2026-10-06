package io.embrace.android.embracesdk.internal.api.delegate

import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.propagation.TextMapGetter
import io.opentelemetry.kotlin.propagation.TextMapPropagator
import io.opentelemetry.kotlin.propagation.TextMapSetter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LateBindingOpenTelemetryTest {

    @Test
    fun `propagator captured before start binds late`() {
        var started = false
        val fields = listOf("traceparent")
        val sdk = object : OpenTelemetry by NoopOpenTelemetry {
            override val propagator: TextMapPropagator = object : TextMapPropagator {
                override fun fields(): Collection<String> = fields
                override fun <T> inject(context: Context, carrier: T?, setter: TextMapSetter<T>) = Unit
                override fun <T> extract(context: Context, carrier: T?, getter: TextMapGetter<T>): Context = context
            }
        }
        val otel = LateBindingOpenTelemetry { if (started) sdk else NoopOpenTelemetry }
        val propagator = otel.propagator
        assertTrue(propagator.fields().isEmpty())

        started = true
        assertEquals(fields, propagator.fields())
    }
}
