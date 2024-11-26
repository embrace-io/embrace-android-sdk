package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class NdkJniInterfaceTest : NativeTestSuite() {

    private val delegate = JniDelegateImpl()

    @Before
    fun setUp() {
        // install signal handlers first so we can test the other methods without race conditions
        val result = delegate.installSignalHandlers(
            "report_path",
            "markerFilePath",
            "null",
            "app_state",
            "report_id",
            29,
            false,
            true
        )
        // we can't really check the result because it's a void function, but if the class is moved or renamed this will fail
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun updateMetaDataTest() {
        val result = delegate.updateMetaData("new_device_meta_data")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun updateSessionIdTest() {
        val result = delegate.onSessionChange("new_session_id")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun updateAppStateTest() {
        val result = delegate.updateAppState("new_app_state")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun getCrashReportTest() {
        val result = delegate.getCrashReport("path")
        assertEquals(null, result)
    }

    @Test
    fun checkForOverwrittenHandlersTest() {
        val result = delegate.checkForOverwrittenHandlers()
        assertEquals(null, result)
    }

    @Test
    fun reinstallSignalHandlersTest() {
        val result = delegate.reinstallSignalHandlers()
        assertEquals(false, result)
    }
}
