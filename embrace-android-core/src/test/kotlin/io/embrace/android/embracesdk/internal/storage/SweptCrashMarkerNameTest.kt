package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SweptCrashMarkerNameTest {

    @Test
    fun `the crash marker is allowed`() {
        assertTrue(SdkDir.INSTALL_STATE.allowedEntries(listOf(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)))
    }
}
