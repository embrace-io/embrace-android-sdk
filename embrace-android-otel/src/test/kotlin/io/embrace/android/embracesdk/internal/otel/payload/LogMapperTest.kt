package io.embrace.android.embracesdk.internal.otel.payload

import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.kotlin.attributes.AnyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class LogMapperTest {

    @Test
    fun `body is serialized by content`() {
        assertEquals("wrapped", body(AnyValue.StringValue("wrapped")))
        assertEquals("true", body(AnyValue.BoolValue(true)))
        assertEquals("3", body(AnyValue.LongValue(3L)))
        assertEquals("1.5", body(AnyValue.DoubleValue(1.5)))
        assertEquals("[1, 2]", body(AnyValue.BytesValue(byteArrayOf(1, 2))))
        assertEquals("[a, 1]", body(AnyValue.ListValue(listOf(AnyValue.StringValue("a"), AnyValue.LongValue(1)))))
        assertEquals("{k=v}", body(AnyValue.MapValue(mapOf("k" to AnyValue.StringValue("v")))))
        assertEquals("[1, 2]", body(byteArrayOf(1, 2)))
        assertEquals("42", body(42L))
        assertEquals("plain", body("plain"))
        assertNull(body(AnyValue.NullValue))
        assertNull(body(null))
    }

    @Test
    fun `attributes are serialized by content`() {
        val log = FakeReadWriteLogRecord(
            attributes = mapOf(
                "any" to AnyValue.StringValue("wrapped"),
                "bytes" to byteArrayOf(1, 2),
                "long" to 5L,
            ),
        ).toEmbracePayload()
        assertEquals(
            listOf(Attribute("any", "wrapped"), Attribute("bytes", "[1, 2]"), Attribute("long", "5")),
            log.attributes,
        )
    }

    private fun body(value: Any?): String? = FakeReadWriteLogRecord(body = value).toEmbracePayload().body
}
