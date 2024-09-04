package io.embrace.android.embracesdk.ndk.jni

import io.embrace.android.embracesdk.internal.ndk.NdkDelegateImpl
import io.embrace.android.embracesdk.ndk.NativeTestSuite
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class NdkJniInterfaceTest : NativeTestSuite() {

    private val ndkDelegate = NdkDelegateImpl()

    @Before
    fun setUp() {
        // install signal handlers first so we can test the other methods without race conditions
        val result = ndkDelegate._installSignalHandlers(
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
        val result = ndkDelegate._updateMetaData("new_device_meta_data")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun updateSessionIdTest() {
        val result = ndkDelegate._updateSessionId("new_session_id")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun updateAppStateTest() {
        val result = ndkDelegate._updateAppState("new_app_state")
        assertEquals(Unit.javaClass, result.javaClass)
    }

    @Test
    fun getCrashReportTest() {
        val result = ndkDelegate._getCrashReport("path")
        assertEquals(null, result)
    }

    @Test
    fun getErrorsTest() {
        val result = ndkDelegate._getErrors("path")
        assertEquals(null, result)
    }

    @Test
    fun checkForOverwrittenHandlersTest() {
        val result = ndkDelegate._checkForOverwrittenHandlers()
        assertEquals(null, result)
    }

    @Test
    fun reinstallSignalHandlersTest() {
        val result = ndkDelegate._reinstallSignalHandlers()
        assertEquals(false, result)
    }
}
