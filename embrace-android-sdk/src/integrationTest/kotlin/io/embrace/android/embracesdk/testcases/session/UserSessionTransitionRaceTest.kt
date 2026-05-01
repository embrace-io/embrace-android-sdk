package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionTransitionRaceTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
        ).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    @Test
    fun `log call invoked before session reaches its max duration but processed after is attributed to the new session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds,
                ),
            ),
            testCaseAction = {
                val workerExecutor = BlockingScheduledExecutorService(
                    fakeClock = testRule.setup.fakeClock,
                    blockingMode = true,
                )

                recordSession()
                workerExecutor.submit { embrace.logInfo("late-log") }
                clock.tick(maxDurationMs + 1L)
                recordSession {
                    workerExecutor.runCurrentlyBlocked()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val log = getSingleLogEnvelope().getLastLog()
                val logSessionId = log.attributes?.findAttributeValue("session.id")
                assertEquals(newUserSessionId, logSessionId)
            },
        )
    }

    @Test
    fun `log call invoked after session reaches its max duration but processed before new session is attributed to the old session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds,
                ),
            ),
            testCaseAction = {
                val workerExecutor = BlockingScheduledExecutorService(
                    fakeClock = testRule.setup.fakeClock,
                    blockingMode = true,
                )

                recordSession {
                    workerExecutor.submit { embrace.logInfo("late-log") }
                    clock.tick(maxDurationMs + 1L)
                    workerExecutor.runCurrentlyBlocked()
                }

                clock.tick(maxDurationMs + 1L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val sessionSpanFromOldPart = checkNotNull(sessions[0].getSessionSpan())
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val log = getSingleLogEnvelope().getLastLog()
                val logSessionId = log.attributes?.findAttributeValue("session.id")
                assertEquals(oldUserSessionId, logSessionId)
                assertTrue(checkNotNull(log.timeUnixNano) <= checkNotNull(sessionSpanFromOldPart.endTimeNanos))
            },
        )
    }

    @Test
    fun `log queued before endUserSession but processed after is attributed to the post-end session`() {
        testRule.runTest(
            testCaseAction = {
                val workerExecutor = BlockingScheduledExecutorService(
                    fakeClock = testRule.setup.fakeClock,
                    blockingMode = true,
                )

                recordSession {
                    clock.tick(10_000L)
                    workerExecutor.submit { embrace.logInfo("late-log") }
                    embrace.endUserSession()
                    workerExecutor.runCurrentlyBlocked()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val u1 = sessions[0].getUserSessionId()
                val u2 = sessions[1].getUserSessionId()
                assertNotEquals(u1, u2)

                val log = getSingleLogEnvelope().getLastLog()
                val logSessionId = log.attributes?.findAttributeValue("session.id")
                assertEquals(u2,logSessionId)
            },
        )
    }
}
