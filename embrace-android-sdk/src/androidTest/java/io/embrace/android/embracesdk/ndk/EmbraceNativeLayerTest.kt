package io.embrace.android.embracesdk.ndk

import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceNativeLayerTest {

    companion object {
        init {
            System.loadLibrary("embrace-native")
            System.loadLibrary("embrace-native-test")
        }
    }

    external fun run(): Int

    @Test
    fun testNativeLayer() {
        val status = run()
        assertEquals(
            "Native layer test failed with status ${status}. Check logcat for further info.",
            0,
            status
        )
    }
}
