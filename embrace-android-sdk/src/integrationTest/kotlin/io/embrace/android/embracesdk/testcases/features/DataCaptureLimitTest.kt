package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DataCaptureLimitTest: RobolectricTest() {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `data capture limit reset between sessions`() {
        testRule.runTest(
            testCaseAction = {
                val msg = "Hello, world!"
                recordSession {
                    repeat(200) {
                        embrace.addBreadcrumb(msg)
                    }
                }
                recordSession {
                    repeat(300) {
                        embrace.addBreadcrumb(msg)
                    }
                }
            },
            assertAction = {
                val envelopes = getSessionEnvelopes(2)
                assertBreadcrumbsMatchLimit(envelopes[0])
                assertBreadcrumbsMatchLimit(envelopes[1])
            }
        )
    }

    private fun assertBreadcrumbsMatchLimit(envelope: Envelope<SessionPayload>) {
        val sessionSpan = envelope.findSessionSpan()
        val crumbs = sessionSpan.findEventsOfType(EmbType.System.Breadcrumb)
        assertEquals(100, crumbs.size)
    }
}
