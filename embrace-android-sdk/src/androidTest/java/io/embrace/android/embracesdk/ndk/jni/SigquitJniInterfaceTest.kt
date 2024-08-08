package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.internal.anr.sigquit.EmbraceSigquitNdkDelegate
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SigquitJniInterfaceTest : NativeTestSuite() {

    private val sigquitNdkDelegate = EmbraceSigquitNdkDelegate()

    @Test
    fun installGoogleAnrHandlerTest() {
        val result = sigquitNdkDelegate.installGoogleAnrHandler(1)
        assertEquals(0, result)
    }
}
