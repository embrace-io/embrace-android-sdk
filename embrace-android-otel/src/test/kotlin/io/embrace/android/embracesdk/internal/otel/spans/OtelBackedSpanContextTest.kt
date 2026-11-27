package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.context.SpanContext
import io.embrace.android.embracesdk.context.TraceFlags
import io.embrace.android.embracesdk.context.TraceState
import io.opentelemetry.kotlin.tracing.createSpanContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OtelBackedSpanContextTest {

    private val otel = createSpanContext(TRACE_ID, SPAN_ID) {
        isSampled = true
        isRandom = true
        isRemote = true
        traceState { put("vendor", "value") }
    }

    @Test
    fun `exposes every OTel span context property and unwraps to the same instance`() {
        val ctx = otel.toEmbraceSpanContext()
        assertEquals(TRACE_ID, ctx.traceId)
        assertEquals(SPAN_ID, ctx.spanId)
        assertArrayEquals(otel.traceIdBytes, ctx.traceIdBytes)
        assertArrayEquals(otel.spanIdBytes, ctx.spanIdBytes)
        assertTrue(ctx.traceFlags.isSampled)
        assertTrue(ctx.traceFlags.isRandom)
        assertTrue(ctx.isValid)
        assertTrue(ctx.isRemote)
        assertEquals("value", ctx.traceState.get("vendor"))
        assertNull(ctx.traceState.get("missing"))
        assertEquals(mapOf("vendor" to "value"), ctx.traceState.asMap())
        assertEquals(otel.toEmbraceSpanContext(), ctx)
        assertSame(otel, ctx.toOtelSpanContext())
    }

    @Test
    fun `a caller-implemented span context converts to an equivalent OTel span context`() {
        val converted = CustomSpanContext().toOtelSpanContext()
        assertEquals(TRACE_ID, converted.traceId)
        assertEquals(SPAN_ID, converted.spanId)
        assertTrue(converted.traceFlags.isSampled)
        assertFalse(converted.traceFlags.isRandom)
        assertTrue(converted.isRemote)
        assertEquals(mapOf("vendor" to "value"), converted.traceState.asMap())
    }

    private class CustomSpanContext : SpanContext {
        override val traceId: String = TRACE_ID
        override val traceIdBytes: ByteArray = createSpanContext(TRACE_ID, SPAN_ID).traceIdBytes
        override val spanId: String = SPAN_ID
        override val spanIdBytes: ByteArray = createSpanContext(TRACE_ID, SPAN_ID).spanIdBytes
        override val traceFlags: TraceFlags = object : TraceFlags {
            override val isSampled: Boolean = true
            override val isRandom: Boolean = false
        }
        override val isValid: Boolean = true
        override val isRemote: Boolean = true
        override val traceState: TraceState = object : TraceState {
            private val entries = mapOf("vendor" to "value")
            override fun get(key: String): String? = entries[key]
            override fun asMap(): Map<String, String> = entries
        }
    }

    private companion object {
        const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
        const val SPAN_ID = "b7ad6b7169203331"
    }
}
