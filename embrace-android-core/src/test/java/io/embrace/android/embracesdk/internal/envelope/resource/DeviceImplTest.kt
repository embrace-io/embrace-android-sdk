package io.embrace.android.embracesdk.internal.envelope.resource

import android.os.Environment
import android.view.WindowManager
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
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
            fakeBackgroundWorker(),
            SystemInfo(),
            EmbLoggerImpl(),
        )

        assertEquals("200x300", device.screenResolution)
    }
}
