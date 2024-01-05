package io.embrace.android.embracesdk

import android.content.res.Configuration
import io.embrace.android.embracesdk.payload.Orientation
import org.junit.Assert.assertEquals
import org.junit.Test

internal class OrientationTest {

    private val testOrientation = Orientation(
        "p",
        12345678L
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("orientation_expected.json", testOrientation)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Orientation>("orientation_expected.json")
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
