package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace.LastRunEndState
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Validation of the basic and miscellaneous functionality of the Android SDK
 */
@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PublicApiTest {

    companion object {
        val validPattern = Regex("^00-" + "[0-9a-fA-F]{32}" + "-" + "[0-9a-fA-F]{16}" + "-01$")
    }

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        EmbraceSetupInterface().apply {
            overriddenConfigService.networkSpanForwardingBehavior =
                FakeNetworkSpanForwardingBehavior(true)
        }
    }

    @Test
    fun `SDK can start`() {
        testRule.runTest(
            preSdkStartAction = {
                assertFalse(embrace.isStarted)
            },
            testCaseAction = {
                assertEquals(AppFramework.NATIVE, configService.appFramework)
                assertFalse(configService.isSdkDisabled())
                assertTrue(embrace.isStarted)
            }
        )
    }

    @Test
    fun `SDK start defaults to native app framework`() {
        testRule.runTest(
            testCaseAction = {
                assertEquals(AppFramework.NATIVE, configService.appFramework)
                assertTrue(embrace.isStarted)
            }
        )
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.sdkDisabled = true
            },
            testCaseAction = {
                assertFalse(embrace.isStarted)
            }
        )
    }

    @Test
    fun `custom appId must be valid`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                assertFalse(embrace.setAppId(""))
                assertFalse(embrace.setAppId("abcd"))
                assertFalse(embrace.setAppId("abcdef"))
                assertTrue(embrace.setAppId("abcde"))
            }
        )
    }

    @Test
    fun `custom appId cannot be set after start`() {
        testRule.runTest(
            testCaseAction = {
                assertTrue(embrace.isStarted)
                assertFalse(embrace.setAppId("xyz12"))
            }
        )
    }

    @Test
    fun `getCurrentSessionId returns null when SDK is not started`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                assertNull(embrace.currentSessionId)
            }
        )
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and foreground session is active`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    assertEquals(
                        embrace.currentSessionId,
                        testRule.setup.overriddenOpenTelemetryModule.currentSessionSpan.getSessionId()
                    )
                    assertNotNull(embrace.currentSessionId)
                }
            }
        )
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and background session is active`() {
        var foregroundSessionId: String? = null
        var backgroundSessionId: String? = null
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    foregroundSessionId = embrace.currentSessionId
                }
                backgroundSessionId = embrace.currentSessionId
            },
            assertAction = {
                assertNotNull(backgroundSessionId)
                assertNotEquals(foregroundSessionId, backgroundSessionId)
            }
        )
    }

    @Test
    fun `getLastRunEndState() behave as expected`() {
        testRule.runTest(
            preSdkStartAction = {
                assertEquals(LastRunEndState.INVALID, embrace.lastRunEndState)
            },
            testCaseAction = {
                assertEquals(LastRunEndState.CLEAN_EXIT, embrace.lastRunEndState)
            }
        )
    }

    @Test
    fun `ensure all generated W3C traceparent conforms to the expected format`() {
        testRule.runTest(
            testCaseAction = {
                repeat(100) {
                    assertTrue(validPattern.matches(checkNotNull(embrace.generateW3cTraceparent())))
                }
            }
        )
    }
}
