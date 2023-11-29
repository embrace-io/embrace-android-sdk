package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.JsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class JsExceptionTest {

    private val serializer = EmbraceSerializer()
    private val info = JsException(
        "java.lang.IllegalStateException",
        "Whoops!",
        "JsError",
        "foo(:20:21)"
    )

    @Test
    fun testSerialization() {
        val data = ResourceReader.readResourceAsText("js_exception_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(info)
        assertEquals(data, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("js_exception_expected.json")
        val obj = serializer.fromJson(json, JsException::class.java)
        assertEquals("java.lang.IllegalStateException", obj.name)
        assertEquals("Whoops!", obj.message)
        assertEquals("JsError", obj.type)
        assertEquals("foo(:20:21)", obj.stacktrace)
    }

    @Test
    fun testEmptyObject() {
        val info = serializer.fromJson("{}", JsException::class.java)
        assertNotNull(info)
        info.name
    }
}
