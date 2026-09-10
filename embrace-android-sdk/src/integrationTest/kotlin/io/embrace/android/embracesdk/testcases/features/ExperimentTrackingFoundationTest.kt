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
        var sharedIdExperimentStartMs: Long = -1
        var variantlessExperimentStartMs: Long = -1
        var untrackEndMs: Long = -1

        testRule.runTest(
            preSdkStartAction = {
                bufferedExperimentStartMs = clock.now()

                embrace.trackExperiments(
                    listOf(
                        embrace.createExperiment("checkout-flow", "variant-a", bufferedExperimentStartMs),
                        embrace.createExperiment("color-palette", "yellow", bufferedExperimentStartMs),
                    ),
                )
                embrace.trackFeatureFlags(
                    listOf(
                        embrace.createFeatureFlag("dark-mode", bufferedExperimentStartMs),
                        embrace.createFeatureFlag("new-ui", bufferedExperimentStartMs),
                    ),
                )
                embrace.trackExperiment("color-palette", "blue")
            },
            testCaseAction = {
                recordSession {
                    flagStartMs = clock.tick()
                    embrace.trackFeatureFlags(
                        listOf(
                            embrace.createFeatureFlag("dark-mode"),
                            embrace.createFeatureFlag("http3"),
                        ),
                    )

                    // an experiment may share an id with a feature flag: they are two independent records
                    sharedIdExperimentStartMs = clock.tick()
                    embrace.trackExperiment(id = "dark-mode", startedAt = sharedIdExperimentStartMs)

                    // omitted timestamp resolves to the SDK clock's time at the moment of the call
                    variantlessExperimentStartMs = clock.tick()
                    embrace.trackExperiment(id = "promo")

                    untrackEndMs = clock.tick()
                    embrace.untrackExperiment(id = "checkout-flow", endedAt = untrackEndMs)

                    embrace.trackExperiments(
                        listOf(embrace.createExperiment(id = "checkout-flow", variant = "variant-b", startedAt = clock.tick())),
                    )
                }
                recordSession()
            },
            assertAction = {
                val expectedRecords =
                    "e:checkout-flow:variant-a:$bufferedExperimentStartMs:$untrackEndMs;" +
                        "e:color-palette:yellow:$bufferedExperimentStartMs;" +
                        "f:dark-mode::$bufferedExperimentStartMs;" +
                        "f:new-ui::$bufferedExperimentStartMs;" +
                        "f:http3::$flagStartMs;" +
                        "e:dark-mode::$sharedIdExperimentStartMs;" +
                        "e:promo::$variantlessExperimentStartMs"
                assertEquals(
                    expectedRecords,
                    testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords(),
                )

                val envelopes = getSessionEnvelopes(2)
                val attrs = checkNotNull(envelopes.first().findSessionPartSpan().attributes)
                assertEquals("5", attrs.findAttributeValue("emb.usage.track_experiment"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.untrack_experiment"))
                assertEquals("2", attrs.findAttributeValue("emb.usage.track_feature_flag"))

                envelopes.forEach { envelope ->
                    assertEquals(
                        expectedRecords,
                        envelope.findSessionPartSpan().attributes?.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS),
                    )
                }
            },
        )
    }
}
