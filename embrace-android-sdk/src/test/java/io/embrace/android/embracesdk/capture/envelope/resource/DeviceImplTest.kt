package io.embrace.android.embracesdk.capture.envelope.resource

import android.os.Environment
import android.view.WindowManager
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

internal class DeviceImplTest {

    companion object {
        private val preferencesService: EmbracePreferencesService = mockk(relaxed = true)
        private val cpuInfoDelegate: FakeCpuInfoDelegate = FakeCpuInfoDelegate()
        private val windowManager = mockk<WindowManager>()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(Environment::class)

            initPreferences()

            every { Environment.getDataDirectory() }.returns(File("ANDROID_DATA"))
        }

        @After
        fun tearDown() {
            unmockkAll()
        }

        private fun initPreferences() {
            every { preferencesService.screenResolution }.returns("200x300")
        }
    }

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )
    }

    @Test
    fun `test screen resolution from preferences`() {
        val device = DeviceImpl(
            windowManager,
            preferencesService,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            cpuInfoDelegate
        )

        assertEquals("200x300", device.screenResolution)
    }

    @Test
    fun getCpuName() {
        val device = DeviceImpl(
            windowManager,
            preferencesService,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            cpuInfoDelegate
        )

        assertEquals("fake_cpu", device.cpuName)
    }

    @Test
    fun getEgl() {
        val device = DeviceImpl(
            windowManager,
            preferencesService,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            cpuInfoDelegate
        )

        assertEquals("fake_egl", device.eglInfo)
    }
}
