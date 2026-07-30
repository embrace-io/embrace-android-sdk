package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior.Companion.DEFAULT_BREADCRUMB_LIMIT
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DataCaptureLimitTest {

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

    /**
     * The session span caps breadcrumbs at [BreadcrumbBehavior.getCustomBreadcrumbLimit] as well as the breadcrumb data
     * source. Raising the remote limit above [DEFAULT_BREADCRUMB_LIMIT] proves that the session span reads the
     * configured value rather than falling back to the default.
     */
    @Test
    fun `remotely raised breadcrumb limit is honoured by the session span`() {
        val raisedLimit = DEFAULT_BREADCRUMB_LIMIT + 50
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(uiConfig = UiRemoteConfig(breadcrumbs = raisedLimit)),
            testCaseAction = {
                recordSession {
                    repeat(raisedLimit + 10) {
                        embrace.addBreadcrumb("Hello, world!")
                    }
                }
            },
            assertAction = {
                val sessionPartSpan = getSessionEnvelopes(1).single().findSessionPartSpan()
                assertEquals(raisedLimit, sessionPartSpan.findEventsOfType(EmbType.System.Breadcrumb).size)
            }
        )
    }

    private fun assertBreadcrumbsMatchLimit(envelope: Envelope<SessionPartPayload>) {
        val sessionPartSpan = envelope.findSessionPartSpan()
        val crumbs = sessionPartSpan.findEventsOfType(EmbType.System.Breadcrumb)
        assertEquals(DEFAULT_BREADCRUMB_LIMIT, crumbs.size)
    }
}
