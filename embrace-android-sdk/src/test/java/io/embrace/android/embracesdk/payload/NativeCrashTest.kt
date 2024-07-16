package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.internal.payload.NativeCrash
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCrashTest {

    private val info = NativeCrash(
        "id",
        "crashMessage",
        mapOf("key" to "value"),
        listOf(
            NativeCrashDataError(
                5,
                2
            )
        ),
        2,
        "map"
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("native_crash_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<NativeCrash>("native_crash_expected.json")
        assertEquals("id", obj.id)
        assertEquals("crashMessage", obj.crashMessage)
        assertEquals(mapOf("key" to "value"), obj.symbols)
        assertEquals(2, obj.unwindError)
        assertEquals("map", obj.map)

        val err = obj.errors?.single()
        assertEquals(5, err?.number)
        assertEquals(2, err?.context)
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<NativeCrash>()
        assertNotNull(obj)
    }
}
