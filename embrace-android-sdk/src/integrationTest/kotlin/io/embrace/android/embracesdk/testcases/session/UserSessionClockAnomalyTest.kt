package io.embrace.android.embracesdk.testcases.session

import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
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
        EmbraceSetupInterface().apply { getEmbLogger().throwOnInternalError = false }
    }

    @Test
    fun `clock shifting backwards when creating new session part terminates existing user session and logs internal error`() {
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
                val logger = testRule.bootstrapper.initModule.logger as FakeInternalLogger

                assertTrue(logger.internalErrorMessages.any {
                    it.msg == InternalErrorType.ClockBackwardsShift.toString()
                })
            }
        )
    }

    @Test
    fun `clock shifting backwards when loading existing user session terminates existing user session and logs internal error`() {
        // write a session that started in the future
        val staleUserSessionId = "stale-session-id"
        testRule.runTest(
            setupAction = {
                val futureStartMs = testRule.bootstrapper.initModule.clock.now() + 100_000L
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

                val logger = testRule.bootstrapper.initModule.logger as FakeInternalLogger
                assertTrue(logger.internalErrorMessages.any {
                    it.msg == InternalErrorType.ClockBackwardsShift.toString()
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
