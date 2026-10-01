package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.SessionPartTimestamps
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbViewAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class ActivityFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `automatically capture activities`() {
        var timestamps: SessionPartTimestamps? = null

        testRule.runTest(
            testCaseAction = {
                timestamps = recordSession()
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val viewSpan = message.findSpanOfType(EmbType.Ux.View)

                viewSpan.attributes?.assertMatches(
                    mapOf(
                        EmbViewAttributes.VIEW_NAME to "android.app.Activity"
                    )
                )
                assertNull(viewSpan.attributes?.findAttributeValue(EmbCommonAttributes.EMB_MANUAL_INSTRUMENTATION))

                with(checkNotNull(timestamps)) {
                    assertEquals(foregroundTimeMs, viewSpan.startTimeNanos?.nanosToMillis())
                    assertEquals(endTimeMs, viewSpan.endTimeNanos?.nanosToMillis())
                }
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(1, EmbType.Ux.View)
                assertSpansMatchGoldenFile(spans, "ux-view-export.json")
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
