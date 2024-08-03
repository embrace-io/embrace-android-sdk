@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
internal class ConfigServiceTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `SDK can start`() {
        with(testRule) {
            assertFalse(embrace.isStarted)
            startSdk()
            assertEquals(AppFramework.NATIVE, harness.appFramework)
            assertFalse(harness.overriddenConfigService.isSdkDisabled())
            assertTrue(embrace.isStarted)
        }
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        with(testRule) {
            harness.overriddenConfigService.sdkDisabled = true
            startSdk()
            assertFalse(embrace.isStarted)
        }
    }

    @Test
    fun `disabling SDK will not resort to a stopping after foregrounding agian`() {
        with(testRule) {
            startSdk()
            assertTrue(embrace.isStarted)
            with(harness) {
                val session = recordSession {
                    harness.overriddenConfigService.sdkDisabled = true
                    harness.overriddenConfigService.updateListeners()
                }
                assertNotNull(session)
                assertNotNull(recordSession())
                assertTrue(embrace.isStarted)
            }
        }
    }
}
