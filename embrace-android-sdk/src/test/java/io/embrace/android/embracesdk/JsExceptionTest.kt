package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.JsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class JsExceptionTest {

    private val info = JsException(
        "java.lang.IllegalStateException",
        "Whoops!",
        "JsError",
        "foo(:20:21)"
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("js_exception_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<JsException>("js_exception_expected.json")
        assertEquals("java.lang.IllegalStateException", obj.name)
        assertEquals("Whoops!", obj.message)
        assertEquals("JsError", obj.type)
        assertEquals("foo(:20:21)", obj.stacktrace)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<JsException>()
        assertNotNull(obj)
    }
}
