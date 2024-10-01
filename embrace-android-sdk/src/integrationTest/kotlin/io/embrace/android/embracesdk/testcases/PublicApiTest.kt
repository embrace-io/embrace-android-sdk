@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace.LastRunEndState
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework
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
        with(testRule.action) {
            assertFalse(embrace.isStarted)
            startSdk()
            assertEquals(AppFramework.NATIVE, configService.appFramework)
            assertFalse(configService.isSdkDisabled())
            assertTrue(embrace.isStarted)
        }
    }

    @Test
    fun `SDK start defaults to native app framework`() {
        with(testRule.action) {
            assertFalse(embrace.isStarted)
            startSdk()
            assertEquals(AppFramework.NATIVE, configService.appFramework)
            assertTrue(embrace.isStarted)
        }
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        with(testRule.action) {
            configService.sdkDisabled = true
            startSdk()
            assertFalse(embrace.isStarted)
        }
    }

    @Test
    fun `custom appId must be valid`() {
        with(testRule.action) {
            assertFalse(embrace.setAppId(""))
            assertFalse(embrace.setAppId("abcd"))
            assertFalse(embrace.setAppId("abcdef"))
            assertTrue(embrace.setAppId("abcde"))
        }
    }

    @Test
    fun `custom appId cannot be set after start`() {
        with(testRule.action) {
            startSdk()
            assertTrue(embrace.isStarted)
            assertFalse(embrace.setAppId("xyz12"))
        }
    }

    @Test
    fun `getCurrentSessionId returns null when SDK is not started`() {
        with(testRule.action) {
            assertNull(embrace.currentSessionId)
        }
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and foreground session is active`() {
        with(testRule.action) {
            startSdk()
            recordSession {
                assertEquals(
                    embrace.currentSessionId,
                    testRule.harness.overriddenOpenTelemetryModule.currentSessionSpan.getSessionId()
                )
                assertNotNull(embrace.currentSessionId)
            }
        }
    }

    @Test
    fun `getCurrentSessionId returns sessionId when SDK is started and background session is active`() {
        with(testRule.action) {
            startSdk()
            var foregroundSessionId: String? = null
            recordSession {
                foregroundSessionId = embrace.currentSessionId
            }
            val backgroundSessionId = embrace.currentSessionId
            assertNotNull(backgroundSessionId)
            assertNotEquals(foregroundSessionId, backgroundSessionId)
        }
    }

    @Test
    fun `getLastRunEndState() behave as expected`() {
        with(testRule.action) {
            assertEquals(LastRunEndState.INVALID, embrace.lastRunEndState)
            startSdk()
            assertEquals(LastRunEndState.CLEAN_EXIT, embrace.lastRunEndState)
        }
    }

    @Test
    fun `ensure all generated W3C traceparent conforms to the expected format`() {
        with(testRule.action) {
            startSdk()
            repeat(100) {
                assertTrue(validPattern.matches(checkNotNull(embrace.generateW3cTraceparent())))
            }
        }
    }

    @Test
    fun `SDK can be stopped`() {
        with(testRule.action) {
            assertFalse(embrace.isStarted)
            startSdk()
            assertTrue(embrace.isStarted)
            stopSdk()
            assertFalse(embrace.isStarted)
        }
    }
}
