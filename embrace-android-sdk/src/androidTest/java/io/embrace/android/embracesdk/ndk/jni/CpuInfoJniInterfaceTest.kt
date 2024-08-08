package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.internal.capture.cpu.EmbraceCpuInfoNdkDelegate
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CpuInfoJniInterfaceTest : NativeTestSuite() {

    private val cpuInfoDelegate = EmbraceCpuInfoNdkDelegate()

    @Test
    fun getNativeCpuNameTest() {
        val cpuName = cpuInfoDelegate.getNativeCpuName()
        assertEquals("", cpuName)
    }

    @Test
    fun getNativeEglTest() {
        val nativeEglName = cpuInfoDelegate.getNativeEgl()
        assertEquals("emulation", nativeEglName)
    }
}