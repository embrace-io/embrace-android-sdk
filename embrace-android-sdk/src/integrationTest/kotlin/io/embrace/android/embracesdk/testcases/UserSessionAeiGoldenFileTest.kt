package io.embrace.android.embracesdk.testcases

import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertLogPayloadMatchesGoldenFile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that AEI logs contain the active session IDs in emb.*_id and the AEI session IDs in aei_*_id.
 * If no user session is active at the time of sending, the emb.*_id attributes should be empty.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionAeiGoldenFileTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(workersToFake = listOf(Worker.Background.NonIoRegWorker))
    }

    @Test
    fun `aei log during active session`() {
        testRule.runTest(
            setupAction = { setupFakeAeiData(listOf(anr.toAeiObject())) },
            testCaseAction = {
                recordSession {
                    testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                }
            },
            assertAction = {
                val sessionEnvelope = getSingleSessionEnvelope()
                assertLogPayloadMatchesGoldenFile(
                    envelope = getSingleLogEnvelope(),
                    expectedUserSessionId = sessionEnvelope.getSessionId(),
                    expectedSessionPartId = sessionEnvelope.getSessionPartId(),
                    goldenFile = "user_session_aei_log_active.json",
                )
            }
        )
    }

    @Test
    fun `aei log without active session`() {
        var pendingUserSessionId: String? = null
        testRule.runTest(
            setupAction = { setupFakeAeiData(listOf(anr.toAeiObject())) },
            testCaseAction = {
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                pendingUserSessionId = checkNotNull(
                    testRule.bootstrapper.userSessionOrchestrationModule.sessionOrchestrator.currentUserSession()
                ).userSessionId
            },
            assertAction = {
                // the process started in the background, so the AEI log belongs to the
                // flavour-pending user session created at process start, with no session part
                assertLogPayloadMatchesGoldenFile(
                    envelope = getSingleLogEnvelope(),
                    expectedUserSessionId = checkNotNull(pendingUserSessionId),
                    expectedSessionPartId = "",
                    goldenFile = "user_session_aei_log_no_session.json",
                )
            }
        )
    }

    private companion object {
        val anr = TestAeiData(
            ApplicationExitInfo.REASON_ANR,
            0,
            "aei",
            "user input dispatch timed out",
        )
    }
}
