package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionManualEndTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
        ).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    @Ignore("Bug: you should be able to end a user session in the background if background activity is enabled")
    @Test
    fun `endUserSession from BG ends the user session when an active BG session-part is in flight`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f),
            ),
            testCaseAction = {
                recordSession()
                // We are now in BG with a BG session-part in flight (BG activity capture is on).
                embrace.endUserSession()
                recordSession()
            },
            assertAction = {
                val fgSessions = getSessionEnvelopes(2, AppState.FOREGROUND)
                val bgSessions = getSessionEnvelopes(2, AppState.BACKGROUND)

                // After the fix, the manual end finalises the in-flight user session and the
                // next FG mints a fresh one.
                assertNotEquals(
                    "Manual end from BG (with bgActivity enabled) should produce a new user session on next FG.",
                    fgSessions[0].getUserSessionId(),
                    fgSessions[1].getUserSessionId(),
                )

                // At least one envelope from the first user session should carry MANUAL — its
                // exact placement (FG vs BG span) depends on which is "current" at the time of the
                // call; we accept either.
                val firstUserSessionId = fgSessions[0].getUserSessionId()
                val firstUserSessionEnvelopes =
                    (fgSessions + bgSessions).filter { it.getUserSessionId() == firstUserSessionId }
                val hasManualReason = firstUserSessionEnvelopes.any { envelope ->
                    envelope.findSessionSpan().attributes
                        ?.findAttributeValue(EMB_USER_SESSION_TERMINATION_REASON) == MANUAL
                }
                assertTrue(
                    "Some envelope from the just-ended user session should carry MANUAL termination reason.",
                    hasManualReason,
                )
            },
        )
    }

    @Test
    fun `endUserSession from background when background activity is off does not end user session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                embrace.endUserSession()
                recordSession()
            },
            assertAction = {
                val parts = getSessionEnvelopes(2)
                assertEquals(parts[0].getUserSessionId(), parts[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `sessionConfig kill-switch does not suppress timer-driven inactivity session transition`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                sessionConfig = SessionRemoteConfig(isEnabled = true),
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                val workerExecutor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)
                recordSession()
                embrace.endUserSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L + 1L)
                workerExecutor.runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }
}
