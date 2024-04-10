package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.hasEventOfType
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.IdGeneratorTest.Companion.validPattern
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Before
    fun before() {
        ApkToolsConfig.IS_SDK_DISABLED = false
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
    fun `SDK disabled via the binary cannot start`() {
        with(testRule) {
            ApkToolsConfig.IS_SDK_DISABLED = true
            startSdk(context = harness.overriddenCoreModule.context)
            assertFalse(embrace.isStarted)
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
                assertEquals(embrace.currentSessionId, harness.overriddenOpenTelemetryModule.currentSessionSpan.getSessionId())
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
    fun `ensure all generated W3C traceparent conforms to the expected format`() {
        with(testRule) {
            repeat(100) {
                assertTrue(validPattern.matches(embrace.generateW3cTraceparent()))
            }
        }
    }
}
