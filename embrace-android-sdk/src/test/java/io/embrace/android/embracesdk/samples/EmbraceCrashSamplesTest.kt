package io.embrace.android.embracesdk.samples

import io.embrace.android.embracesdk.Embrace
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceCrashSamplesTest {

    companion object {
        private lateinit var crashSampleTest: EmbraceCrashSamples

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(Embrace::class)
            crashSampleTest = EmbraceCrashSamples
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Test
    fun `test isSdkStarted with isStarted false throws EmbraceNotInitializedException`() {
        every { Embrace.getInstance().isStarted } returns false
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.isSdkStarted() }
    }

    @Test
    fun `test checkAnrDetectionEnabled throws EmbraceAnrDisabledException if isAnrCaptureEnabled is false`() {
        every { Embrace.getInstance().internalInterface.isAnrCaptureEnabled() } returns false
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.checkAnrDetectionEnabled() }
    }

    @Test
    fun `test checkNdkDetectionEnabled throws EmbraceNotInitializedException if Embrace isStarted is false`() {
        every { Embrace.getInstance().isStarted } returns false
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.checkNdkDetectionEnabled() }
    }

    @Test
    fun `test checkNdkDetectionEnabled with isNdkEnabled false throws EmbraceNdkDisabledException`() {
        every { Embrace.getInstance().isStarted } returns true
        every { Embrace.getInstance().internalInterface.isNdkEnabled() } returns false
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.checkNdkDetectionEnabled() }
    }

    @Test
    fun `test throwJvmException actually throws EmbraceNotInitializedException if Embrace isStarted is false`() {
        every { Embrace.getInstance().isStarted } returns false
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.throwJvmException() }
    }

    @Test
    fun `test throwJvmException actually throws EmbraceNotInitializedException if Embrace is initialized`() {
        every { Embrace.getInstance().isStarted } returns true
        assertThrows(EmbraceSampleCodeException::class.java) { crashSampleTest.throwJvmException() }
    }
}
