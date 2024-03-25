package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AppFrameworkAdapterTest {
    @Test
    fun testSerialization() {
        val adapter = AppFrameworkAdapter()
        val result = adapter.toJson(EnvelopeResource.AppFramework.NATIVE)
        assertEquals(1, result)
    }

    @Test
    fun testDeserialization() {
        val adapter = AppFrameworkAdapter()
        val result = adapter.fromJson(1)
        assertEquals(EnvelopeResource.AppFramework.NATIVE, result)
    }
}
