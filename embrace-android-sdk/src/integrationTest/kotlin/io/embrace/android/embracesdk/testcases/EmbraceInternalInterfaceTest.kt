package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class EmbraceInternalInterfaceTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `no NPEs when SDK not started`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
                with(EmbraceInternalApi.internalInterface) {
                    assertFalse(shouldCaptureNetworkBody("", ""))
                    assertFalse(isNetworkSpanForwardingEnabled())
                }
                assertFalse(embrace.isStarted)
            }
        )
    }

    @Test
    fun `access check methods work as expected`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                disabledUrlPatterns = setOf("dontlogmebro.pizza"),
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 10000,
                        method = "GET",
                        urlRegex = "capture.me",
                        expiresIn = 10000
                    )
                )
            ),
            testCaseAction = {
                recordSession {
                    assertTrue(
                        EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(
                            "capture.me",
                            "GET"
                        )
                    )
                    assertFalse(
                        EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(
                            "capture.me",
                            "POST"
                        )
                    )
                    assertFalse(EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(URL, "GET"))
                    assertFalse(EmbraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled())
                }
            }
        )
    }

    @Test
    fun `SDK will not start if feature flag has it being disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(threshold = 0),
            expectSdkToStart = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
            }
        )
    }

    companion object {
        private const val URL = "https://embrace.io"
    }
}
