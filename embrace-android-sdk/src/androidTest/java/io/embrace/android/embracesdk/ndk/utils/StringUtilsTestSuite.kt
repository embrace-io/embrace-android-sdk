package io.embrace.android.embracesdk.ndk.utils

import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Test

internal class StringUtilsTestSuite : NativeTestSuite() {

    external fun run(): Int

    @Test
    fun testStringUtils() = runNativeTestSuite(::run)
}
