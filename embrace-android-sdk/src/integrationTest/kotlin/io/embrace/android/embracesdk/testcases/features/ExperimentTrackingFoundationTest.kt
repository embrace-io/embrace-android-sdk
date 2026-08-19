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
                embrace.trackExperiment(id = "checkout-flow", variant = "variant-a", startedAt = bufferedExperimentStartMs)
            },
            testCaseAction = {
                recordSession {
                    flagStartMs = clock.tick()
                    embrace.trackFeatureFlag(id = "dark-mode", startedAt = flagStartMs)

                    // an experiment may share an id with a feature flag: they are two independent records
                    sharedIdExperimentStartMs = clock.tick()
                    embrace.trackExperiment(id = "dark-mode", startedAt = sharedIdExperimentStartMs)

                    // omitted timestamp resolves to the SDK clock's time at the moment of the call
                    variantlessExperimentStartMs = clock.tick()
                    embrace.trackExperiment(id = "promo")

                    untrackEndMs = clock.tick()
                    embrace.untrackExperiment("checkout-flow", endedAt = untrackEndMs)

                    embrace.trackExperiments {
                        experiment(id = "checkout-flow", variant = "variant-b", startedAt = clock.tick())
                    }
                }
                recordSession()
            },
            assertAction = {
                val expectedRecords =
                    "e:checkout-flow:variant-a:$bufferedExperimentStartMs:$untrackEndMs;" +
                        "f:dark-mode::$flagStartMs;" +
                        "e:dark-mode::$sharedIdExperimentStartMs;" +
                        "e:promo::$variantlessExperimentStartMs"
                assertEquals(
                    expectedRecords,
                    testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords()
                )

                val envelopes = getSessionEnvelopes(2)
                val attrs = checkNotNull(envelopes.first().findSessionPartSpan().attributes)
                assertEquals("4", attrs.findAttributeValue("emb.usage.track_experiment"))
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

    @Test
    fun `a single block can declare both experiments and feature flags`() {
        var expOneStartMs: Long = -1
        var flagOneStartMs: Long = -1
        var expTwoStartMs: Long = -1
        var untrackEndMs: Long = -1

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    expOneStartMs = clock.tick()
                    flagOneStartMs = clock.tick()
                    expTwoStartMs = clock.tick()
                    embrace.trackExperiments {
                        experiment(id = "checkout-flow", variant = "variant-a", startedAt = expOneStartMs)
                        featureFlag(id = "dark-mode", startedAt = flagOneStartMs)
                        experiment(id = "promo", startedAt = expTwoStartMs)
                    }

                    untrackEndMs = clock.tick()
                    embrace.untrackExperiments {
                        experiment(id = "promo", endedAt = untrackEndMs)
                        featureFlag(id = "dark-mode", endedAt = untrackEndMs)
                    }
                }
            },
            assertAction = {
                val expectedRecords =
                    "e:checkout-flow:variant-a:$expOneStartMs;" +
                        "f:dark-mode::$flagOneStartMs:$untrackEndMs;" +
                        "e:promo::$expTwoStartMs:$untrackEndMs"
                assertEquals(
                    expectedRecords,
                    testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords()
                )

                val attrs = checkNotNull(getSingleSessionEnvelope().findSessionPartSpan().attributes)
                assertEquals(expectedRecords, attrs.findAttributeValue(EmbCommonAttributes.EMB_EXPERIMENTS))

                // each kind present in a block records its usage once, however many entries of that kind were declared
                assertEquals("1", attrs.findAttributeValue("emb.usage.track_experiment"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.track_feature_flag"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.untrack_experiment"))
                assertEquals("1", attrs.findAttributeValue("emb.usage.untrack_feature_flag"))
            }
        )
    }
}
