package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.ExecutorName.*
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a timed session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class TimedSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        IntegrationTestRule.Harness(
            fakeClock = clock,
            workerThreadModule = FakeWorkerThreadModule(clock, BACKGROUND_REGISTRATION),
        )
    }

    @Test
    fun `timed session automatically ends session`() {
        with(testRule) {
            val executor =
                harness.workerThreadModule.scheduledExecutor(BACKGROUND_REGISTRATION) as BlockingScheduledExecutorService
            harness.fakeConfigService.sessionBehavior = fakeSessionBehavior(
                localCfg = { SessionLocalConfig(90) }) {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                repeat(4) {
                    executor.moveForwardAndRunBlocked(90000)
                }
            }
            val messages = harness.getSentSessionMessages()
            assertEquals(5, messages.size)
            verifySessionHappened(messages[0])
            verifySessionHappened(messages[1])
            verifySessionHappened(messages[2])
            verifySessionHappened(messages[3])
            verifySessionHappened(messages[4])
            assertNotEquals(messages[0].session.sessionId, messages[1].session.sessionId)
        }
    }

    @Test
    fun `timed session has no effect when config disabled`() {
        with(testRule) {
            val executor =
                harness.workerThreadModule.scheduledExecutor(BACKGROUND_REGISTRATION) as BlockingScheduledExecutorService
            harness.fakeConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                executor.moveForwardAndRunBlocked(90000)
            }
            val message = harness.getSentSessionMessages().single()
            verifySessionHappened(message)
        }
    }
}
