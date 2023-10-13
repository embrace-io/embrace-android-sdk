package io.embrace.android.embracesdk

import com.google.gson.Gson
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
        val data = ResourceReader.readResourceAsText("js_exception_expected.json")
            .filter { !it.isWhitespace() }
        val observed = Gson().toJson(info)
        assertEquals(data, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("js_exception_expected.json")
        val obj = Gson().fromJson(json, JsException::class.java)
        assertEquals("java.lang.IllegalStateException", obj.name)
        assertEquals("Whoops!", obj.message)
        assertEquals("JsError", obj.type)
        assertEquals("foo(:20:21)", obj.stacktrace)
    }

    @Test
    fun testEmptyObject() {
        val info = Gson().fromJson("{}", JsException::class.java)
        assertNotNull(info)
        info.name
    }
}
