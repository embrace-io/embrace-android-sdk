package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertSessionSpanMatchesGoldenFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the golden-file output for the max-duration session-end scenario.
 *
 * Uses a faked NonIoRegWorker (with blockingMode = false so that immediate tasks still
 * execute synchronously) so that the scheduled max-duration timer can be fired explicitly
 * via runCurrentlyBlocked() inside the test action.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionTimeoutGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.NonIoRegWorker)).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    /**
     * A session ends due to max duration being exceeded. The scheduled timer is fired
     * explicitly after advancing the fake clock, so the final-session-part attributes are
     * stamped on the correct foreground session span.
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
                assertSessionSpanMatchesGoldenFile(
                    sessions[0],
                    "user_session_max_duration_1.json",
                )
                assertSessionSpanMatchesGoldenFile(
                    sessions[1],
                    "user_session_max_duration_2.json",
                )
            }
        )
    }
}
