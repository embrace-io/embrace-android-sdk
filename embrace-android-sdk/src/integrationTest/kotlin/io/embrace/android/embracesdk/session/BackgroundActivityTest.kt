package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.fakeBackgroundActivityBehavior
import io.embrace.android.embracesdk.getSavedBackgroundActivities
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifyBackgroundActivityMessage
import io.embrace.android.embracesdk.verifySessionHappened
import io.embrace.android.embracesdk.worker.ExecutorName
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that background activities can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class BackgroundActivityTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        IntegrationTestRule.Harness(
            fakeClock = clock,
            workerThreadModule = FakeWorkerThreadModule(clock, ExecutorName.SESSION_CACHE_EXECUTOR)
        )
    }

    @Test
    fun `background activity messages are recorded`() {
        with(testRule) {
            harness.fakeConfigService.backgroundActivityBehavior = fakeBackgroundActivityBehavior {
                BackgroundActivityRemoteConfig(threshold = 100f)
            }
            val executor =
                harness.workerThreadModule.scheduledExecutor(ExecutorName.SESSION_CACHE_EXECUTOR) as BlockingScheduledExecutorService

            harness.recordSession {
                embrace.addBreadcrumb("Session1")
            }

            // capture a background activity
            harness.fakeClock.tick(20000)

            // capture another session & verify it's a new session.
            harness.recordSession {
                embrace.addBreadcrumb("Session2")
            }

            // validate sessions
            val messages = harness.getSentSessionMessages()
            assertEquals(4, messages.size)

            // validate background activities
            executor.runCurrentlyBlocked()
            val backgroundActivities = harness.getSavedBackgroundActivities()
            assertEquals(4, backgroundActivities.size)
            backgroundActivities.forEach(::verifyBackgroundActivityMessage)
        }
    }
}
