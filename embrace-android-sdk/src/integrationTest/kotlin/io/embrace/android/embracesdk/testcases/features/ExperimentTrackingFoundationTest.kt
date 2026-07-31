package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExperimentTrackingFoundationTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `experiment and feature flag tracking works through the public api`() {
        var bufferedExperimentStartMs: Long = -1
        var flagStartMs: Long = -1
        var variantlessExperimentStartMs: Long = -1
        var untrackEndMs: Long = -1

        testRule.runTest(
            preSdkStartAction = {
                bufferedExperimentStartMs = clock.now()
                embrace.trackExperiment(
                    embrace.createExperiment(
                        id = "checkout-flow",
                        startTimeMs = bufferedExperimentStartMs,
                        variant = "variant-a",
                    )
                )
            },
            testCaseAction = {
                recordSession {
                    flagStartMs = clock.tick()
                    embrace.trackFeatureFlag(
                        embrace.createFeatureFlag(id = "dark-mode", startTimeMs = flagStartMs)
                    )

                    variantlessExperimentStartMs = clock.tick()
                    embrace.trackExperiment(
                        embrace.createExperiment(id = "promo", startTimeMs = variantlessExperimentStartMs)
                    )

                    untrackEndMs = clock.tick()
                    embrace.untrackExperiment("checkout-flow", endTimeMs = untrackEndMs)

                    embrace.trackExperiment(
                        embrace.createExperiment(id = "checkout-flow", startTimeMs = clock.tick(), variant = "variant-b")
                    )
                }
                recordSession()
            },
            assertAction = {
                val expectedRecords =
                    "e:checkout-flow:variant-a:$bufferedExperimentStartMs:$untrackEndMs;" +
                        "f:dark-mode::$flagStartMs;" +
                        "e:promo::$variantlessExperimentStartMs"
                assertEquals(
                    expectedRecords,
                    testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords()
                )

                val envelopes = getSessionEnvelopes(2)
                val attrs = checkNotNull(envelopes.first().findSessionPartSpan().attributes)
                assertEquals("3", attrs.findAttributeValue("emb.usage.track_experiment"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.untrack_experiment"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.track_feature_flag"))

                envelopes.forEach { envelope ->
                    assertEquals(
                        expectedRecords,
                        envelope.findSessionPartSpan().attributes?.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS)
                    )
                }
            }
        )
    }
}
