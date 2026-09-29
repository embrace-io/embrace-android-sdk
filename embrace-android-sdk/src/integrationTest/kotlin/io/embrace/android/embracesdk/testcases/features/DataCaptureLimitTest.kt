package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.BreadcrumbConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class DataCaptureLimitTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

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
     * The session span caps breadcrumbs at [BreadcrumbConfig.customLimit] as well as the breadcrumb data
     * source. Raising the remote limit above [BreadcrumbConfig.DEFAULT_LIMIT] proves that the session span reads the
     * configured value rather than falling back to the default.
     */
    @Test
    fun `remotely raised breadcrumb limit is honoured by the session span`() {
        val raisedLimit = BreadcrumbConfig.DEFAULT_LIMIT + 50
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

    @Suppress("DEPRECATION")
    @Test
    fun `low span event limit is honoured by the session span`() {
        val limit = 20
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                dataConfig = DataRemoteConfig(maxSpanEventsPerSessionPart = limit)
            ),
            testCaseAction = {
                recordSession {
                    repeat(limit + 30) {
                        embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
                    }
                }
            },
            assertAction = {
                val sessionPartSpan = getSessionEnvelopes(1).single().findSessionPartSpan()
                assertEquals(limit, checkNotNull(sessionPartSpan.events).size)
            }
        )
    }

    private fun assertBreadcrumbsMatchLimit(envelope: Envelope<SessionPartPayload>) {
        val sessionPartSpan = envelope.findSessionPartSpan()
        val crumbs = sessionPartSpan.findEventsOfType(EmbType.System.Breadcrumb)
        assertEquals(BreadcrumbConfig.DEFAULT_LIMIT, crumbs.size)
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
