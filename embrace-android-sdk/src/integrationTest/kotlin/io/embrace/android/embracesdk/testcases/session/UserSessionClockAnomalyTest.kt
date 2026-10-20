package io.embrace.android.embracesdk.testcases.session

import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertDistinctUserSessions
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Asserts that clock anomalies can terminate the current user session and immediately start a new one.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserSessionClockAnomalyTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface().apply { getFakeErrorHandler().throwOnSdkError = false }
    }

    @Test
    fun `clock shifting backwards when creating new session part terminates existing user session and logs SDK error`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                // shift clock backwards
                clock.setCurrentTime(clock.now() - 60 * 1000)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2, assertOrdering = false)
                assertDistinctUserSessions(sessions[0], sessions[1])
                val errorHandler = testRule.services.errorHandler as FakeErrorHandler

                assertTrue(errorHandler.sdkErrorMessages.any {
                    it.msg == SdkErrorType.ClockBackwardsShift.toString()
                })
            }
        )
    }

    @Test
    fun `clock shifting backwards when loading existing user session terminates existing user session and logs SDK error`() {
        // write a session that started in the future
        val staleUserSessionId = "stale-session-id"
        testRule.runTest(
            setupAction = {
                val futureStartMs = fakeClock.now() + 100_000L
                persistUserSession(
                    userSessionId = staleUserSessionId,
                    startMs = futureStartMs,
                    lastActivityMs = futureStartMs,
                )
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(staleUserSessionId, session.getUserSessionId())

                val errorHandler = testRule.services.errorHandler as FakeErrorHandler
                assertTrue(errorHandler.sdkErrorMessages.any {
                    it.msg == SdkErrorType.ClockBackwardsShift.toString()
                })
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
