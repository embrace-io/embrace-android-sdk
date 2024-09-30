package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `session span event limits do not affect logging maximum breadcrumbs`() {
        with(testRule) {
            startSdk()
            harness.recordSession {
                repeat(101) {
                    embrace.addBreadcrumb("breadcrumb $it")
                }
            }
            val session = harness.getSingleSession()
            assertEquals(100, session.getSessionSpan()?.events?.size)
        }
    }
}
