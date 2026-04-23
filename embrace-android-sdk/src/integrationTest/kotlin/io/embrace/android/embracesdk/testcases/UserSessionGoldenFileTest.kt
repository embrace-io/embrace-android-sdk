package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertLogPayloadMatchesGoldenFile
import io.embrace.android.embracesdk.testframework.assertions.assertSessionSpanMatchesGoldenFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the session span and log payload emitted by the SDK for basic scenarios match
 * known-good JSON golden files.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session span matches golden file for basic session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertSessionSpanMatchesGoldenFile(
                    getSingleSessionEnvelope(),
                    "user_session_basic_span.json",
                )
            }
        )
    }

    @Test
    fun `log payload matches golden file for basic info log`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logInfo("Hi")
                }
            },
            assertAction = {
                assertLogPayloadMatchesGoldenFile(
                    getSingleLogEnvelope(),
                    "user_session_basic_log.json",
                )
            }
        )
    }
}
