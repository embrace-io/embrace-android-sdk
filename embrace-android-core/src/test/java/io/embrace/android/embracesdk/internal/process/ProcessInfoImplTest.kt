package io.embrace.android.embracesdk.internal.process

import android.os.Build.VERSION_CODES
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class ProcessInfoImplTest {

    private val fakeDeviceStartTime = 100_000L
    private lateinit var processInfo: ProcessInfo

    @Before
    fun setUp() {
        processInfo = ProcessInfoImpl(fakeDeviceStartTime, BuildVersionChecker)
    }

    @Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
    @Test
    fun `verify start time in T`() {
        val startRequestElapsedTime = Process.getStartRequestedElapsedRealtime()
        val startRequestedEpochTime = checkNotNull(processInfo.startRequestedTimeMs())
        assertEquals(startRequestElapsedTime, startRequestedEpochTime - fakeDeviceStartTime)
    }

    @Config(sdk = [VERSION_CODES.N])
    @Test
    fun `verify start time in N`() {
        val startElapsedTime = Process.getStartElapsedRealtime()
        val startRequestedEpochTime = checkNotNull(processInfo.startRequestedTimeMs())
        assertEquals(startElapsedTime, startRequestedEpochTime - fakeDeviceStartTime)
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify start time in L`() {
        assertNull(processInfo.startRequestedTimeMs())
    }
}
