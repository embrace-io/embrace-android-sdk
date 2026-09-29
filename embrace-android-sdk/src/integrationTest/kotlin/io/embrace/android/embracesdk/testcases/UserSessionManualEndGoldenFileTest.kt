package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.SessionPartDiff
import io.embrace.android.embracesdk.testframework.assertions.UserSessionDiff
import io.embrace.android.embracesdk.testframework.assertions.assertPayloadsMatchGoldenFiles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Verifies the session part spans emitted when a user session is manually ended.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserSessionManualEndGoldenFileTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    /**
     * Manually ending a session starts a new one and sets the termination reason correctly
     */
    @Test
    fun `manual end of session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(20000)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertPayloadsMatchGoldenFiles(
                    UserSessionDiff(SessionPartDiff(sessions[0], "user_session_manual_end_1.json")),
                    UserSessionDiff(SessionPartDiff(sessions[1], "user_session_manual_end_2.json")),
                )
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
