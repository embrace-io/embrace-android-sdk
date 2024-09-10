@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.Embrace.LastRunEndState
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.createNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.recordSession
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
        IntegrationTestRule.Harness(startImmediately = false).apply {
            overriddenConfigService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        }
    }

    @Test
    fun `SDK can start`() {
        with(testRule) {
            assertFalse(embrace.isStarted)
            startSdk(context = harness.overriddenCoreModule.context)
            assertEquals(AppFramework.NATIVE, harness.appFramework)
            assertFalse(harness.overriddenConfigService.isSdkDisabled())
            assertTrue(embrace.isStarted)
        }
    }

    @Test
    fun `SDK start defaults to native app framework`() {
        with(testRule) {
            assertFalse(embrace.isStarted)
            startSdk(context = harness.overriddenCoreModule.context)
            assertEquals(AppFramework.NATIVE, harness.appFramework)
            assertTrue(embrace.isStarted)
        }
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        with(testRule) {
            harness.overriddenConfigService.sdkDisabled = true
            startSdk(context = harness.overriddenCoreModule.context)
            assertFalse(embrace.isStarted)
        }
    }

    @Test
    fun `custom appId must be valid`() {
        with(testRule) {
            assertFalse(embrace.setAppId(""))
            assertFalse(embrace.setAppId("abcd"))
            assertFalse(embrace.setAppId("abcdef"))
            assertTrue(embrace.setAppId("abcde"))
        }
    }

    @Test
    fun `custom appId cannot be set after start`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            assertTrue(embrace.isStarted)
            assertFalse(embrace.setAppId("xyz12"))
        }
    }

    @Test
    fun `getCurrentSessionId returns null when SDK is not started`() {
        with(testRule) {
            assertNull(embrace.currentSessionId)
        }
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and foreground session is active`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            harness.recordSession {
                assertEquals(
                    embrace.currentSessionId,
                    harness.overriddenOpenTelemetryModule.currentSessionSpan.getSessionId()
                )
                assertNotNull(embrace.currentSessionId)
            }
        }
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and background session is active`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            var foregroundSessionId: String? = null
            harness.recordSession {
                foregroundSessionId = embrace.currentSessionId
            }
            val backgroundSessionId = embrace.currentSessionId
            assertNotNull(backgroundSessionId)
            assertNotEquals(foregroundSessionId, backgroundSessionId)
        }
    }

    @Test
    fun `getLastRunEndState() behave as expected`() {
        with(testRule) {
            assertEquals(LastRunEndState.INVALID, embrace.lastRunEndState)
            startSdk()
            assertEquals(LastRunEndState.CLEAN_EXIT, embrace.lastRunEndState)
        }
    }

    @Test
    fun `ensure all generated W3C traceparent conforms to the expected format`() {
        with(testRule) {
            startSdk()
            repeat(100) {
                assertTrue(validPattern.matches(checkNotNull(embrace.generateW3cTraceparent())))
            }
        }
    }

    @Test
    fun `SDK can be stopped`() {
        with(testRule) {
            assertFalse(embrace.isStarted)
            startSdk(context = harness.overriddenCoreModule.context)
            assertTrue(embrace.isStarted)
            stopSdk()
            assertFalse(embrace.isStarted)
        }
    }
}
