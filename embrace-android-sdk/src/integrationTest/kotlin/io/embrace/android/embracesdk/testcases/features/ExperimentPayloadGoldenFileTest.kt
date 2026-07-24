package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.experiments.TrackedExperiment
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.Placeholder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures what the experiment state looks like in the payloads that carry it, so that the
 * delivery mechanism's effect on the payload shape is visible in the golden files.
 */
@RunWith(AndroidJUnit4::class)
internal class ExperimentPayloadGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session part payload with experiment state matches golden file`() {
        testRule.runTest(
            preSdkStartAction = {
                declareExperiments()
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                validatePayloadAgainstGoldenFile(
                    payload = envelope,
                    goldenFileName = "experiment_session.json",
                    placeholders = mapOf(
                        Placeholder.USER_SESSION_ID to envelope.getUserSessionId(),
                        Placeholder.SESSION_PART_ID to envelope.getSessionPartId(),
                    ),
                )
            },
        )
    }

    @Test
    fun `log batch payload with experiment state matches golden file`() {
        testRule.runTest(
            preSdkStartAction = {
                declareExperiments()
            },
            testCaseAction = {
                recordSession {
                    repeat(5) {
                        embrace.logInfo("experiment log message ${it + 1}")
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                validatePayloadAgainstGoldenFile(
                    payload = getSingleLogEnvelope(),
                    goldenFileName = "experiment_log_batch.json",
                    placeholders = mapOf(
                        Placeholder.USER_SESSION_ID to session.getUserSessionId(),
                        Placeholder.SESSION_PART_ID to session.getSessionPartId(),
                    ),
                )
            },
        )
    }

    private fun io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface.declareExperiments() {
        embrace.trackExperiment(
            TrackedExperiment("checkout-flow", "variant-a", BUCKETED_AT_1_MS),
            TrackedExperiment("new-onboarding", "control", BUCKETED_AT_2_MS),
        )
    }

    private companion object {
        private const val BUCKETED_AT_1_MS = 1691000000000L
        private const val BUCKETED_AT_2_MS = 1691000100000L
    }
}
