package io.embrace.android.embracesdk.ndk

import org.junit.Assert.assertEquals

/**
 * Base class for native tests. This loads the pre-requisite native libraries & provides
 * a convenience function for running native test suites.
 */
internal open class NativeTestSuite {

    companion object {
        init {
            System.loadLibrary("embrace-native")
            System.loadLibrary("embrace-native-test")
        }
    }

    /**
     * Runs the native test suite. The action should be a JNI function that returns an int denoting
     * exit status of the test run.
     */
    fun runNativeTestSuite(action: () -> Int) {
        val status = action()
        assertEquals(
            "Native layer test failed with status ${status}. Search logcat for 'Embrace' to see the native test results.",
            0,
            status
        )
    }
}
