package io.embrace.android.embracesdk.internal.payload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class AppFrameworkTest {

    @Test
    fun `from int`() {
        assertEquals(AppFramework.NATIVE, AppFramework.fromInt(1))
        assertEquals(AppFramework.REACT_NATIVE, AppFramework.fromInt(2))
        assertEquals(AppFramework.UNITY, AppFramework.fromInt(3))
        assertEquals(AppFramework.FLUTTER, AppFramework.fromInt(4))
    }

    @Test
    fun `from string`() {
        assertNull(AppFramework.fromString(null))
        assertEquals(AppFramework.NATIVE, AppFramework.fromString("native"))
        assertEquals(AppFramework.REACT_NATIVE, AppFramework.fromString("react_native"))
        assertEquals(AppFramework.UNITY, AppFramework.fromString("unity"))
        assertEquals(AppFramework.FLUTTER, AppFramework.fromString("flutter"))
    }
}
