package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SESSION_COUNT = 200

@RunWith(AndroidJUnit4::class)
internal class SessionSpamTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session messages are recorded`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            testCaseAction = {
                repeat(SESSION_COUNT) {
                    recordSession {
                        embrace.addBreadcrumb("Hello, World!")
                    }
                }
            },
            assertAction = {
                val messages = getSessionEnvelopes(SESSION_COUNT, waitTimeMs = 10000)
                val ids = messages.map { it.getSessionId() }.toSet()
                assertEquals(SESSION_COUNT, ids.size)

                val bas = getSessionEnvelopes(SESSION_COUNT, ApplicationState.BACKGROUND, waitTimeMs = 10000)
                val baIds = bas.map { it.getSessionId() }.toSet()
                assertEquals(SESSION_COUNT, baIds.size)
            }
        )
    }
}
