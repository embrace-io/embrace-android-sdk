package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class AppFrameworkSerializerTest {

    @Test
    fun `encodes app framework as its integer value`() {
        assertEquals("1", embraceJson.encodeToString(AppFrameworkSerializer, AppFramework.NATIVE))
        assertEquals("2", embraceJson.encodeToString(AppFrameworkSerializer, AppFramework.REACT_NATIVE))
        assertEquals("3", embraceJson.encodeToString(AppFrameworkSerializer, AppFramework.UNITY))
        assertEquals("4", embraceJson.encodeToString(AppFrameworkSerializer, AppFramework.FLUTTER))
    }

    @Test
    fun `decodes known integer values`() {
        assertEquals(AppFramework.NATIVE, embraceJson.decodeFromString(AppFrameworkSerializer, "1"))
        assertEquals(AppFramework.REACT_NATIVE, embraceJson.decodeFromString(AppFrameworkSerializer, "2"))
        assertEquals(AppFramework.UNITY, embraceJson.decodeFromString(AppFrameworkSerializer, "3"))
        assertEquals(AppFramework.FLUTTER, embraceJson.decodeFromString(AppFrameworkSerializer, "4"))
    }

    @Test
    fun `decodes unknown integer value as null`() {
        assertNull(embraceJson.decodeFromString(AppFrameworkSerializer, "999"))
    }
}
