package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.SessionPartDiff
import io.embrace.android.embracesdk.testframework.assertions.UserSessionDiff
import io.embrace.android.embracesdk.testframework.assertions.assertPayloadsMatchGoldenFiles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Verifies the golden-file output for the max-duration session-end scenario.
 *
 * Uses a faked NonIoRegWorker (with blockingMode = false so that immediate tasks still
 * execute synchronously) so that the scheduled max-duration timer can be fired explicitly
 * via runCurrentlyBlocked() inside the test action.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserSessionTimeoutGoldenFileTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.NonIoRegWorker)).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    /**
     * A session ends due to max duration being exceeded. The scheduled timer is fired
     * explicitly after advancing the fake clock, so the final-session-part attributes are
     * stamped on the correct foreground session part span.
     */
    @Test
    fun `session end max duration`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val behavior = testRule.bootstrapper.configService.sessionBehavior
                    clock.tick(behavior.getMaxSessionDurationMs() + 1)
                    testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertPayloadsMatchGoldenFiles(
                    UserSessionDiff(SessionPartDiff(sessions[0], "user_session_max_duration_1.json")),
                    UserSessionDiff(SessionPartDiff(sessions[1], "user_session_max_duration_2.json")),
                )
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
