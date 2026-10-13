package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSourceImpl
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Validation of the internal API
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class EmbraceInternalInterfaceTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `access check methods work as expected`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                disabledUrlPatterns = setOf("dontlogmebro.pizza"),
                networkCaptureRules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "test",
                        duration = 10000,
                        method = "GET",
                        urlRegex = "capture.me",
                        expiresIn = 10000
                    )
                )
            ),
            testCaseAction = {
                EmbraceInternalApi.internalInterface.addEnvelopeResource("foo", "bar")
                recordSession {
                    embrace.logInfo("Hi")
                    assertFalse(EmbraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled())
                }
            },
            assertAction = {
                val expected = mapOf(
                    "foo" to "bar",
                    EnvelopeResourceSourceImpl.KEY_OTEL_SDK_MODE to when {
                        otelSdkMode.useKotlinSdk -> EnvelopeResourceSourceImpl.OTEL_SDK_MODE_REGULAR
                        else -> EnvelopeResourceSourceImpl.OTEL_SDK_MODE_COMPAT
                    },
                )
                val sessionResource = checkNotNull(getSingleSessionEnvelope().resource)
                assertEquals(expected, sessionResource.extras)

                val logResource = checkNotNull(getSingleLogEnvelope().resource)
                assertEquals(expected, logResource.extras)
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
