package io.embrace.android.embracesdk

import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.payload.Orientation
import org.junit.Assert.assertEquals
import org.junit.Test

internal class OrientationTest {

    private val serializer = EmbraceSerializer()
    private val testOrientation = Orientation(
        "p",
        12345678L
    )

    @Test
    fun testSerialization() {
        val data = ResourceReader.readResourceAsText("orientation_expected.json")
            .filter { !it.isWhitespace() }
        val observed = serializer.toJson(testOrientation)
        assertEquals(data, observed)
    }

    @Test
    fun testDeserialization() {
        val json = ResourceReader.readResourceAsText("orientation_expected.json")
        val obj = serializer.fromJson(json, Orientation::class.java)
        assertEquals("p", obj.orientation)
        assertEquals(12345678L, obj.timestamp)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, obj.internalOrientation)
    }

    @Test
    fun testInternalOrientation() {
        val portraitOrientation = Orientation("p", 1234L)
        val landscapeOrientation = Orientation("l", 1234L)

        assertEquals(Configuration.ORIENTATION_PORTRAIT, portraitOrientation.internalOrientation)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, landscapeOrientation.internalOrientation)
    }
}
