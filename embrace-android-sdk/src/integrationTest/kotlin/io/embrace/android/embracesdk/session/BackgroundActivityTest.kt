package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifyBgActivityMessage
import io.embrace.android.embracesdk.verifySessionHappened
import io.embrace.android.embracesdk.worker.ExecutorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful background activity can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class BackgroundActivityTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        IntegrationTestRule.Harness(
            fakeClock = clock,
            workerThreadModule = FakeWorkerThreadModule(clock, ExecutorName.SEND_SESSIONS)
        )
    }

    @Test
    fun `bg activity messages are recorded`() {
        with(testRule) {
            harness.recordSession()
            harness.fakeClock.tick(30000)
            harness.recordSession()

            val executor =
                harness.workerThreadModule.scheduledExecutor(ExecutorName.SEND_SESSIONS) as BlockingScheduledExecutorService
            executor.runCurrentlyBlocked()

            // filter out dupes from overwritten saves
            val bgActivities = harness.getSentBackgroundActivities().distinctBy { it.backgroundActivity.sessionId }
            assertEquals(2, bgActivities.size)

            // verify first bg activity
            val first = bgActivities[0]
            verifyBgActivityMessage(first)
            assertEquals(1, first.backgroundActivity.number)

            // verify second bg activity
            val second = bgActivities[1]
            verifyBgActivityMessage(second)
            assertEquals(2, second.backgroundActivity.number)

            // ID should be different for each
            assertNotEquals(first.backgroundActivity.sessionId, second.backgroundActivity.sessionId)
        }
    }
}
