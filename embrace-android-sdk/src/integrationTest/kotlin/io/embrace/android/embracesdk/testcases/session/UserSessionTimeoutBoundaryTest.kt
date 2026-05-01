package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.INACTIVITY
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionTimeoutBoundaryTest {

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
    fun `inactivity timer at exact boundary expires the user session`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                val workerExecutor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)

                recordSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L)
                workerExecutor.runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `max duration timer at exact boundary expires the user session with MAX_DURATION_REACHED`() {
        val maxDurationSeconds = 3600
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds,
                ),
            ),
            testCaseAction = {
                val workerExecutor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)

                recordSession()
                clock.tick(maxDurationSeconds * 1_000L)
                workerExecutor.runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                // The first user session, having reached max-duration, should terminate with MAX_DURATION_REACHED.
                val terminationReason = sessions[0].findSessionSpan().attributes
                    ?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON)
                if (terminationReason != null) {
                    assertEquals(MAX_DURATION_REACHED, terminationReason)
                }
            },
        )
    }

    @Test
    fun `manual end after max duration but before the job is processed terminates with MANUAL`() {
        val maxDurationSeconds = 600
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds,
                ),
            ),
            testCaseAction = {
                val workerExecutor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)

                recordSession {
                    clock.tick(maxDurationSeconds * 1_000L)
                    embrace.endUserSession()
                }
                workerExecutor.runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertEquals(
                    MANUAL,
                    sessions[0].findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON)
                )
            },
        )
    }

    @Test
    fun `foregrounding before inactivity timeout continues the same user session`() {
        runForegroundingTest(tickMs = INACTIVITY_TIMEOUT_MS - 1_000L) { sessions ->
            assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding at inactivity boundary creates a new user session`() {
        runForegroundingTest(tickMs = INACTIVITY_TIMEOUT_MS) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding after inactivity boundary creates a new user session`() {
        runForegroundingTest(tickMs = INACTIVITY_TIMEOUT_MS + 1_000L) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    private fun runForegroundingTest(
        tickMs: Long,
        assertions: (sessions: List<Envelope<SessionPartPayload>>) -> Unit,
    ) {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = INACTIVITY_TIMEOUT_SECONDS),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(tickMs)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertions(sessions)
                val reason = sessions[0].findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON)
                if (reason != null && tickMs >= INACTIVITY_TIMEOUT_MS) {
                    assertEquals(INACTIVITY, reason)
                }
            },
        )
    }

    private companion object {
        const val INACTIVITY_TIMEOUT_SECONDS = 30
        const val INACTIVITY_TIMEOUT_MS = INACTIVITY_TIMEOUT_SECONDS * 1_000L
    }
}
