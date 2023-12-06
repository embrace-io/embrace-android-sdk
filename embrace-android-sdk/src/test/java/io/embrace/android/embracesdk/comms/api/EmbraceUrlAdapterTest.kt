package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceUrlAdapterTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun `test EmbraceUrl serialization`() {
        val embraceUrl = EmbraceUrl.create("http://fake.url")
        val jsonStr: String = serializer.toJson(embraceUrl)
        val serialized: EmbraceUrl = serializer.fromJson(jsonStr, EmbraceUrl::class.java)
        assertEquals(embraceUrl.toString(), serialized.toString())
    }
}
