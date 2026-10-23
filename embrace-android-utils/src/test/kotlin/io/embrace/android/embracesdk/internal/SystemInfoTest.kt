package io.embrace.android.embracesdk.internal

import android.os.Build
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Build.VERSION_CODES.LOLLIPOP_MR1
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.TIRAMISU
import io.embrace.android.embracesdk.internal.utils.VersionChecker
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
    fun `os build is only read from API 23`() {
        forEachSdk(LOLLIPOP, LOLLIPOP_MR1) { versionChecker ->
            assertEquals("", getOsBuild(versionChecker) { BASE_OS })
        }
        forEachSdk(M, TIRAMISU) { versionChecker ->
            assertEquals(BASE_OS, getOsBuild(versionChecker) { BASE_OS })
        }
    }

    @Test
    fun `primary ABI is the first supported ABI listed in the BUILD object`() {
        assertEquals("arm64-v8a", getPrimaryAbi { arrayOf("arm64-v8a", "armeabi-v7a") })
        assertEquals("x86_64", getPrimaryAbi { arrayOf("x86_64", "x86") })
        assertEquals(Build.UNKNOWN, getPrimaryAbi { emptyArray() })

        // Takes the first regardless if subsequent ones are supported
        assertEquals(Build.UNKNOWN, getPrimaryAbi { arrayOf("riscv64", "arm64-v8a") })
    }

    private fun forEachSdk(vararg sdkInts: Int, action: (VersionChecker) -> Unit) {
        sdkInts.forEach { sdkInt ->
            try {
                action(VersionChecker { min -> sdkInt >= min })
            } catch (e: AssertionError) {
                throw AssertionError("Failed at API $sdkInt", e)
            }
        }
    }

    private companion object {
        const val BASE_OS = "base-os"
    }
}
