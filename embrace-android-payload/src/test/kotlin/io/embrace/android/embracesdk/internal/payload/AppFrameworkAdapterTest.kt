package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.serialization.AppFrameworkAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AppFrameworkAdapterTest {
    @Test
    fun testSerialization() {
        val adapter = AppFrameworkAdapter()
        val result = adapter.toJson(AppFramework.NATIVE)
        assertEquals(1, result)
    }

    @Test
    fun testDeserialization() {
        val adapter = AppFrameworkAdapter()
        val result = adapter.fromJson(1)
        assertEquals(AppFramework.NATIVE, result)
    }
}
