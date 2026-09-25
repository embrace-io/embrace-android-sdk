package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.internal.otel.payload.toPayloadString
import io.opentelemetry.kotlin.attributes.AnyValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class TypedAttributesTest {

    private lateinit var mutator: FakeAttributesMutator

    @Before
    fun setUp() {
        mutator = FakeAttributesMutator()
    }

    @Test
    fun `primitives keep their type`() {
        mutator.setTypedAttributes(
            mapOf(
                "string" to "value",
                "boolean" to true,
                "long" to 42L,
                "double" to 1.5,
            ),
        )
        assertEquals(
            mapOf(
                "string" to "value",
                "boolean" to true,
                "long" to 42L,
                "double" to 1.5,
            ),
            mutator.attributes,
        )
    }

    @Test
    fun `whole-valued double stays a double`() {
        mutator.setTypedAttribute("key", 1.0)
        assertEquals(1.0, mutator.attributes["key"])
        assertEquals("1.0", mutator.attributes["key"]?.toPayloadString())
    }

    @Test
    fun `int is widened to long`() {
        mutator.setTypedAttribute("key", 5)
        assertEquals(5L, mutator.attributes["key"])
    }

    @Test
    fun `float is widened to a double that stringifies the same`() {
        listOf(1.1f, 1.0f, 0.1f, 0.001f, 1.0E-4f, 1.0E7f, 3.4028235E38f, -0.0f, Float.NaN).forEach { value ->
            mutator.setTypedAttribute("key", value)
            val stored = mutator.attributes["key"]
            assertEquals(Double::class, stored!!::class)
            assertEquals(value.toString(), stored.toPayloadString())
        }
    }

    @Test
    fun `byte array and any value are passed through`() {
        val bytes = byteArrayOf(1, 2)
        val anyValue = AnyValue.StringValue("wrapped")
        mutator.setTypedAttributes(mapOf("bytes" to bytes, "any" to anyValue))
        assertArrayEquals(bytes, mutator.attributes["bytes"] as ByteArray)
        assertEquals(anyValue, mutator.attributes["any"])
    }

    @Test
    fun `homogeneous lists keep their element type`() {
        mutator.setTypedAttributes(
            mapOf(
                "strings" to listOf("a", "b"),
                "booleans" to listOf(true, false),
                "longs" to listOf(1L, 2L),
                "ints" to listOf(1, 2),
                "doubles" to listOf(1.0, 2.5),
                "floats" to listOf(1.1f, 2.0f),
                "empty" to emptyList<Any>(),
            ),
        )
        assertEquals(
            mapOf(
                "strings" to listOf("a", "b"),
                "booleans" to listOf(true, false),
                "longs" to listOf(1L, 2L),
                "ints" to listOf(1L, 2L),
                "doubles" to listOf(1.0, 2.5),
                "floats" to listOf(1.1, 2.0),
                "empty" to emptyList<String>(),
            ),
            mutator.attributes,
        )
    }

    @Test
    fun `lists of any values and byte arrays become a list value`() {
        mutator.setTypedAttributes(
            mapOf(
                "any" to listOf(AnyValue.LongValue(1), AnyValue.StringValue("a")),
                "bytes" to listOf(byteArrayOf(1, 2)),
            ),
        )
        assertEquals(AnyValue.ListValue(listOf(AnyValue.LongValue(1), AnyValue.StringValue("a"))), mutator.attributes["any"])
        assertEquals(AnyValue.ListValue(listOf(AnyValue.BytesValue(byteArrayOf(1, 2)))), mutator.attributes["bytes"])
    }

    @Test
    fun `unsupported values fall back to their string form`() {
        mutator.setTypedAttributes(
            mapOf(
                "mixed-list" to listOf("a", 1L, null),
                "map" to mapOf("k" to "v"),
                "other" to StringBuilder("built"),
            ),
        )
        assertEquals(
            mapOf(
                "mixed-list" to "[a, 1, null]",
                "map" to "{k=v}",
                "other" to "built",
            ),
            mutator.attributes,
        )
    }
}
