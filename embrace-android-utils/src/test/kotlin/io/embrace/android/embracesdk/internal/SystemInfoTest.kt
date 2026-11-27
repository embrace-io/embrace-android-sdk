package io.embrace.android.embracesdk.internal

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SystemInfoTest {

    @Test
    fun `verify default system info`() {
        with(SystemInfo()) {
            assertEquals("android", osName)
            assertEquals("linux", osType)
            assertEquals(Build.VERSION.SDK_INT.toString(), androidOsApiLevel)
        }
        assertEquals("13", getOsVersion { "13" })
    }

    @Test
    fun `os build is read from the base OS`() {
        assertEquals(BASE_OS, getOsBuild { BASE_OS })
        assertEquals("", getOsBuild { null })
    }

    @Test
    fun `primary ABI is the first supported ABI listed in the BUILD object`() {
        assertEquals("arm64-v8a", getPrimaryAbi { arrayOf("arm64-v8a", "armeabi-v7a") })
        assertEquals("x86_64", getPrimaryAbi { arrayOf("x86_64", "x86") })
        assertEquals(Build.UNKNOWN, getPrimaryAbi { emptyArray() })

        // Takes the first regardless if subsequent ones are supported
        assertEquals(Build.UNKNOWN, getPrimaryAbi { arrayOf("riscv64", "arm64-v8a") })
    }

    private companion object {
        const val BASE_OS = "base-os"
    }
}
