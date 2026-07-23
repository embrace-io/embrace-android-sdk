package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
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

@RunWith(AndroidJUnit4::class)
internal class ActivityFeatureTest {

    @Rule
    @JvmField
    val testRule = SdkIntegrationTestRule()

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
}
