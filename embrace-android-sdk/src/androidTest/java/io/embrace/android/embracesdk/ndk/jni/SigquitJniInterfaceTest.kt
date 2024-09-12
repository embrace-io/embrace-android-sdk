package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.fakes.FakeSigquitDataSource
import io.embrace.android.embracesdk.internal.anr.sigquit.EmbraceSigquitNdkDelegate
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SigquitJniInterfaceTest : NativeTestSuite() {

    private val sigquitNdkDelegate = EmbraceSigquitNdkDelegate()

    /**
     * Besides testing installGoogleAnrHandler(), this is also verifying that the JNI FindClass method called
     * in anr.c -> configure_reporting works.
     * We are currently looking for SigquitDataSource, so if any of it's moved or renamed, this test will fail.
     */
    @Test
    fun installGoogleAnrHandlerTest() {
        val fakeSigquitDataSource = FakeSigquitDataSource()
        val result = sigquitNdkDelegate.installGoogleAnrHandler(1, fakeSigquitDataSource)
        assertEquals(0, result)
    }
}
