package io.embrace.android.embracesdk

import android.app.Activity
import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

internal class EmbraceAutomaticVerificationTest {

    companion object {
        private lateinit var embraceSamples: EmbraceAutomaticVerification
        private val activity: Activity = mockk(relaxed = true)
        private val scheduledExecutorService: ScheduledExecutorService = mockk(relaxed = true)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ScheduledExecutorService::class)
            mockkStatic(ExecutorService::class)
            mockkStatic(EmbraceImpl::class)
            mockkStatic(Embrace::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Before
    fun setup() {
        every { Embrace.getImpl() } returns mockk(relaxed = true)
        embraceSamples = EmbraceAutomaticVerification(scheduledExecutorService)
    }

    @After
    fun after() {
        clearAllMocks(
            answers = false,
            staticMocks = false,
            objectMocks = false
        )
    }

    @Test
    fun `test runEndSession`() {
        with(embraceSamples) {
            every { Embrace.getInstance().endSession() } just runs
            runEndSession()
            verify { Embrace.getInstance().endSession() }
        }
    }

    @Test
    fun `test startVerification that captures IOException`() {
        with(embraceSamples) {
            automaticVerificationChecker = mockk(relaxed = true)
            activityService = FakeActivityService(foregroundActivity = activity)
            verificationActions = mockk(relaxed = true)
            every { automaticVerificationChecker.createFile(activity) } throws IOException("ERROR")

            startVerification()

            verify(exactly = 0) { verificationActions.runActions() }
        }
    }

    @Test
    fun `test startVerification does not run verification steps if marker file exists`() {
        with(embraceSamples) {
            automaticVerificationChecker = mockk(relaxed = true)
            activityService = FakeActivityService(foregroundActivity = activity)
            verificationActions = mockk(relaxed = true)
            every { automaticVerificationChecker.createFile(activity) } returns false

            startVerification()

            verify(exactly = 0) {
                verificationActions.runActions()
            }
        }
    }

    @Test
    fun `test startVerification runs verification steps if marker file does not exist`() {
        with(embraceSamples) {
            automaticVerificationChecker = mockk(relaxed = true)
            activityService = FakeActivityService(foregroundActivity = activity)
            verificationActions = mockk(relaxed = true)
            every { automaticVerificationChecker.createFile(any() as Activity) } returns true
            every { verificationActions.runActions() } just runs

            startVerification()

            verify(exactly = 1) {
                verificationActions.runActions()
            }
        }
    }
}
