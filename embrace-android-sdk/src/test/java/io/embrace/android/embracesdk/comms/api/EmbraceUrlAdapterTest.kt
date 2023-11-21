package io.embrace.android.embracesdk.comms.api

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EmbraceUrlAdapterTest {

    @Test
    fun `test EmbraceUrl serialization`() {
        val embraceUrl = EmbraceUrl.create("http://fake.url")
        val gson = GsonBuilder().registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter()).create()
        val jsonStr = gson.toJson(embraceUrl, EmbraceUrl::class.java)
        val serialized = gson.fromJson(jsonStr, EmbraceUrl::class.java)

        assertEquals(embraceUrl.toString(), serialized.toString())
    }

    @Test
    fun `test null EmbraceUrl serialization`() {
        val gson = GsonBuilder().registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter()).create()
        val jsonStr = gson.toJson(null, EmbraceUrl::class.java)
        val serialized = gson.fromJson(jsonStr, EmbraceUrl::class.java)

        assertNull(serialized)
    }
}
