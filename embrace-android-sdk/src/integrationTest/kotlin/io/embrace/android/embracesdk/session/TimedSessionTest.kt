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
            workerThreadModule = FakeWorkerThreadModule(clock),
        )
    }

    @Test
    fun `timed session automatically ends session`() {
        with(testRule) {
            val executor =
                harness.workerThreadModule.scheduledExecutor(SESSION_CLOSER) as BlockingScheduledExecutorService
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
            assertEquals(10, messages.size)
            verifySessionHappened(messages[0], messages[1])
            verifySessionHappened(messages[2], messages[3])
            verifySessionHappened(messages[4], messages[5])
            verifySessionHappened(messages[6], messages[7])
            verifySessionHappened(messages[8], messages[9])
            assertNotEquals(messages[1].session.sessionId, messages[3].session.sessionId)
        }
    }

    @Test
    fun `timed session has no effect when config disabled`() {
        with(testRule) {
            val executor =
                harness.workerThreadModule.scheduledExecutor(SESSION_CLOSER) as BlockingScheduledExecutorService
            harness.fakeConfigService.sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
            }
            harness.recordSession {
                executor.moveForwardAndRunBlocked(90000)
            }
            val messages = harness.getSentSessionMessages()
            assertEquals(2, messages.size)
            verifySessionHappened(messages[0], messages[1])
        }
    }

    private class FakeWorkerThreadModule(
        fakeClock: FakeClock,
        private val base: WorkerThreadModule = WorkerThreadModuleImpl()
    ) : WorkerThreadModule by base {

        private val executor = BlockingScheduledExecutorService(fakeClock)

        override fun scheduledExecutor(executorName: ExecutorName): ScheduledExecutorService {
            return when (executorName) {
                SESSION_CLOSER -> executor
                else -> base.scheduledExecutor(executorName)
            }
        }
    }
}
