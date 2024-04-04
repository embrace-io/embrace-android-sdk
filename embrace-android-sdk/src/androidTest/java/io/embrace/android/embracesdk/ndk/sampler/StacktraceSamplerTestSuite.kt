package io.embrace.android.embracesdk.ndk.sampler

import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Test

internal class StacktraceSamplerTestSuite : NativeTestSuite() {

    external fun run(): Int

    @Test
    fun testStacktraceSampler() {
        runNativeTestSuite(::run)
    }
}
