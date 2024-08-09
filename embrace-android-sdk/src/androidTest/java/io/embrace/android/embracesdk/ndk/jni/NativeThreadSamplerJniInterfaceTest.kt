package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerNdkDelegate
import io.embrace.android.embracesdk.internal.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Test


internal class NativeThreadSamplerJniInterfaceTest : NativeTestSuite() {

    private val nativeThreadSamplerNdkDelegate = NativeThreadSamplerNdkDelegate()

    @Test
    fun setupNativeThreadSamplerTest() {
        val result = nativeThreadSamplerNdkDelegate.setupNativeThreadSampler(false)
        assertEquals(true, result)
    }

    @Test
    fun monitorCurrentThreadTest() {
        val result = nativeThreadSamplerNdkDelegate.monitorCurrentThread()
        assertEquals(true, result)
    }

    @Test
    fun startSamplingTest() {
        val result = nativeThreadSamplerNdkDelegate.startSampling(1, 500)
        // we can't really check the result because it's a void function, but if the class is moved or renamed this will fail
        assertEquals(Unit.javaClass, result.javaClass)
    }

    /**
     * Besides testing finishSampling(), this is also verifying that the JNI FindClass method called in populate_jni_cache works.
     * We are currently looking for NativeThreadAnrSample and NativeThreadAnrStackframe, so if any of those classes are moved or renamed,
     * this test will fail.
     */
    @Test
    fun finishSamplingTest() {
        val result = nativeThreadSamplerNdkDelegate.finishSampling()
        assertEquals(emptyList<NativeThreadAnrSample>(), result)
    }
}
