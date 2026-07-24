package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.experiment.EMB_EXPERIMENTS_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ExperimentTrackingTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private var preStartBucketTimeMs: Long = 0
    private var secondBucketTimeMs: Long = 0
    private var flagTrackTimeMs: Long = 0
    private var experimentEndTimeMs: Long = 0

    @Test
    fun `experiment state is attached as envelope metadata in the happy path`() {
        testRule.runTest(
            preSdkStartAction = {
                preStartBucketTimeMs = clock.now()
                assertTrue(
                    embrace.trackExperiment(
                        TrackedExperiment("exp1", "variantA", preStartBucketTimeMs),
                    ),
                )
            },
            testCaseAction = {
                recordSession {
                    secondBucketTimeMs = clock.now()
                    assertTrue(
                        embrace.trackExperiment(
                            TrackedExperiment("exp2", "variantB", secondBucketTimeMs),
                        ),
                    )
                    flagTrackTimeMs = clock.now()
                    assertTrue(embrace.trackFeatureFlag(TrackedFeatureFlag("flag1", flagTrackTimeMs)))
                    embrace.logInfo("test message")
                    experimentEndTimeMs = clock.now()
                    assertTrue(embrace.untrackExperiment("exp1", endTimeMs = experimentEndTimeMs))
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                // the first session envelope's metadata carries the full state as of session end:
                // untracking closed exp1's record in place, adding its end time
                assertEquals(
                    "e:exp1:variantA:$preStartBucketTimeMs:$experimentEndTimeMs;" +
                        "e:exp2:variantB:$secondBucketTimeMs;" +
                        "f:flag1::$flagTrackTimeMs",
                    sessions[0].metadata?.experiments,
                )

                // records persist for the entire process (they only age out when a new process
                // starts without re-tracking), so the second session envelope carries the same
                // state, including exp1's closed record
                assertEquals(
                    "e:exp1:variantA:$preStartBucketTimeMs:$experimentEndTimeMs;" +
                        "e:exp2:variantB:$secondBucketTimeMs;" +
                        "f:flag1::$flagTrackTimeMs",
                    sessions[1].metadata?.experiments,
                )

                // untrackExperiment cut the in-flight log batch BEFORE mutating the state, so the
                // log's envelope metadata reflects the state at log time: both experiments and
                // the feature flag tracked, no end time on exp1 yet
                val logEnvelope = getSingleLogEnvelope()
                assertEquals(
                    "e:exp1:variantA:$preStartBucketTimeMs;" +
                        "e:exp2:variantB:$secondBucketTimeMs;" +
                        "f:flag1::$flagTrackTimeMs",
                    logEnvelope.metadata?.experiments,
                )

                // the state lives at the envelope level only: neither the session part span nor
                // the log carries a per-record attribute
                assertNull(
                    checkNotNull(sessions[0].findSessionPartSpan().attributes)
                        .toMap()[EMB_EXPERIMENTS_ATTRIBUTE_KEY],
                )
                val log = logEnvelope.getLogOfType(EmbType.System.Log)
                assertNull(checkNotNull(log.attributes).toMap()[EMB_EXPERIMENTS_ATTRIBUTE_KEY])
            },
        )
    }
}
