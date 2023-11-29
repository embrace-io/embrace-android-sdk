package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EmbraceUrlAdapterTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun `test EmbraceUrl serialization`() {
        val embraceUrl = EmbraceUrl.create("http://fake.url")
        val jsonStr = serializer.toJson(embraceUrl, EmbraceUrl::class.java)
        val serialized = serializer.fromJson(jsonStr, EmbraceUrl::class.java)
        assertEquals(embraceUrl.toString(), serialized.toString())
    }

    @Test
    fun `test null EmbraceUrl serialization`() {
        val jsonStr = serializer.toJson(null, EmbraceUrl::class.java)
        val serialized = serializer.fromJson(jsonStr, EmbraceUrl::class.java)
        assertNull(serialized)
    }
}
