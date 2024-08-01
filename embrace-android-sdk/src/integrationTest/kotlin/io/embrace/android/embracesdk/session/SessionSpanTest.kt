package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.extensions.getSessionSpan
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionSpanTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `there is always a valid session when background activity is enabled`() {
        with(testRule) {
            harness.overriddenConfigService.backgroundActivityCaptureEnabled = true
            startSdk()
            checkNotNull(harness.recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
            })
            assertFalse(embrace.currentSessionId.isNullOrBlank())
            checkNotNull(harness.recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
            })
        }
    }

    @Test
    fun `no valid session when bg activity not enabled after backgrounding`() {
        with(testRule) {
            harness.overriddenConfigService.backgroundActivityCaptureEnabled = false
            startSdk()
            checkNotNull(harness.recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
            })
            assertTrue(embrace.currentSessionId.isNullOrBlank())
            checkNotNull(harness.recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
            })
        }
    }

    @Test
    fun `session span event do no affect logging maximum breadcrumbs`() {
        with(testRule) {
            startSdk()
            val session = checkNotNull(harness.recordSession {
                repeat(101) {
                    embrace.addBreadcrumb("breadcrumb $it")
                }
            })
            assertEquals(100, session.getSessionSpan()?.events?.size)
        }
    }
}
