package io.embrace.android.embracesdk.internal.otel.payload

import org.junit.Assert.assertEquals
import org.junit.Test

internal class PayloadValueExtTest {

    @Test
    fun `payload string matches toString for every type the SDK produces`() {
        val values = listOf(
            "",
            "value",
            "null",
            true,
            false,
            0,
            42,
            -7,
            Int.MAX_VALUE,
            0L,
            42L,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            0.0,
            -0.0,
            1.0,
            1.5,
            0.001,
            1.0E-4,
            1.0E7,
            Double.MAX_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            emptyList<String>(),
            listOf("a", "b"),
            listOf(true, false),
            listOf(1, 2),
            listOf(1L, 2L),
            listOf(1.0, 2.5),
            listOf("a", 1L, null),
        )
        values.forEach { value ->
            assertEquals("Unexpected payload string for $value", value.toString(), value.toPayloadString())
        }
    }

    @Test
    fun `list elements are stringified by content`() {
        assertEquals("[[1, 2], [3]]", listOf(byteArrayOf(1, 2), byteArrayOf(3)).toPayloadString())
    }
}
