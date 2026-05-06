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
 * Verifies the session spans emitted when a user session is manually ended.
 *
 * Lives in its own class so the test rule can fake [Worker.Background.PeriodicCacheWorker]:
 * the periodic snapshot otherwise consumes UUIDs from the seeded [kotlin.random.Random] on a
 * background thread, which makes the post-startup IDs in the golden files non-deterministic.
 * The fake makes the cacher's snapshot job queue but never auto-fire, so all UUID generation
 * stays on the test thread in source order.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionManualEndGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.PeriodicCacheWorker))
    }

    /**
     * Manually ending a session starts a new one and sets the termination reason correctly
     */
    @Test
    fun `manual end of session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(20000)
                    embrace.endUserSession()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertSessionSpanMatchesGoldenFile(
                    sessions[0],
                    "user_session_manual_end_1.json",
                )
                assertSessionSpanMatchesGoldenFile(
                    sessions[1],
                    "user_session_manual_end_2.json",
                )
            }
        )
    }
}
