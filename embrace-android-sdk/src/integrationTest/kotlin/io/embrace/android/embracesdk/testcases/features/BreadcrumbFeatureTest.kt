package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findEventOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.semconv.EmbBreadcrumbAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class BreadcrumbFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `custom breadcrumb feature`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, world!")
                }
                embrace.addBreadcrumb("Bye, world!")
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val message = getSessionEnvelopes(2)
                message.first().assertBreadcrumbWithMessage("Hello, world!")
                val bas = getSessionEnvelopes(2, ProcessState.BACKGROUND)
                bas.last().assertBreadcrumbWithMessage("Bye, world!")
            }
        )
    }

    private fun Envelope<SessionPartPayload>.assertBreadcrumbWithMessage(message: String) {
        findSessionPartSpan().findEventOfType(EmbType.System.Breadcrumb).attributes?.assertMatches(mapOf(
            EmbBreadcrumbAttributes.MESSAGE to message
        ))
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
