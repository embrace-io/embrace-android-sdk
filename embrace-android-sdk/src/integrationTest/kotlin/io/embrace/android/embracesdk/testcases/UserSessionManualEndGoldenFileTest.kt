package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.SessionPartDiff
import io.embrace.android.embracesdk.testframework.assertions.UserSessionDiff
import io.embrace.android.embracesdk.testframework.assertions.assertPayloadsMatchGoldenFiles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the session spans emitted when a user session is manually ended.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionManualEndGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
}
