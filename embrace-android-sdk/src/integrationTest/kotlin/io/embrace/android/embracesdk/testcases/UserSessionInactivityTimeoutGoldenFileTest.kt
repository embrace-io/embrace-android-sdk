package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.SessionPartDiff
import io.embrace.android.embracesdk.testframework.assertions.UserSessionDiff
import io.embrace.android.embracesdk.testframework.assertions.assertPayloadsMatchGoldenFiles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the golden-file output for the inactivity-timeout session-end scenario.
 *
 * Fakes [Worker.Background.NonIoRegWorker] (where the inactivity timer runs) with
 * blockingMode = false so that immediate tasks still execute synchronously while the
 * scheduled timer can be fired explicitly via runCurrentlyBlocked() inside the test
 * action.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionInactivityTimeoutGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
        ).also {
            it.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    /**
     * A user session times out by exceeding the inactivity timeout threshold and starts a new user session.
     */
    @Test
    fun `session inactivity timeout with background activity enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)
            ),
            testCaseAction = {
                val behavior = testRule.bootstrapper.configService.sessionBehavior
                val workerExecutor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)

                // record initial session
                recordSession()

                // background activity should be split at this point
                clock.tick(behavior.getSessionInactivityTimeoutMs() + 1)
                workerExecutor.runCurrentlyBlocked()

                // background activity can continue indefinitely without splitting
                clock.tick(behavior.getMaxSessionDurationMs() * 3)
                workerExecutor.runCurrentlyBlocked()

                // create another user session
                recordSession()
            },
            assertAction = {
                val sessionPartEnvelopes = getSessionEnvelopes(2, AppState.FOREGROUND)
                val bgPartEnvelopes = getSessionEnvelopes(3, AppState.BACKGROUND)

                assertPayloadsMatchGoldenFiles(
                    UserSessionDiff(
                        SessionPartDiff(sessionPartEnvelopes[0], "user_session_bg_timeout_2.json"),
                        SessionPartDiff(bgPartEnvelopes[1], "user_session_bg_timeout_3.json"),
                    ),
                    UserSessionDiff(
                        SessionPartDiff(bgPartEnvelopes[2], "user_session_bg_timeout_4.json")
                    ),
                    UserSessionDiff(
                        SessionPartDiff(sessionPartEnvelopes[1], "user_session_bg_timeout_5.json")
                    ),
                    partsWithNoUserSession = listOf(
                        SessionPartDiff(bgPartEnvelopes[0], "user_session_bg_timeout_1.json")
                    ),
                )
            }
        )
    }
}
