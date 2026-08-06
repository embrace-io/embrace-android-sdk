package io.embrace.android.embracesdk.testcases

import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertLogPayloadMatchesGoldenFile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that AEI logs carry the session IDs of the exit they describe in aei_*_id, and leave the emb.*_id
 * attributes blank. An AEI describes the exit of a previous process, so the session that happens to be
 * active when it is sent is not stamped on it - whether or not there is one.
 *
 * The log does still carry the current state of the SDK in the emb.state* attributes, as that is the only
 * state available by the time the AEI can be reported.
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
                    drainAeiInstrumentation()
                }
            },
            assertAction = {
                val sessionEnvelope = getSingleSessionEnvelope()
                val aeiLog = getSingleLogEnvelope()
                assertLogPayloadMatchesGoldenFile(
                    envelope = aeiLog,
                    expectedUserSessionId = sessionEnvelope.getUserSessionId(),
                    expectedSessionPartId = sessionEnvelope.getSessionPartId(),
                    goldenFile = "user_session_aei_log_active.json",
                )
                // the golden file pins these blank, so the active session cannot have leaked onto the log
                assertTrue(sessionEnvelope.getUserSessionId().isNotBlank())
                assertTrue(sessionEnvelope.getSessionPartId().isNotBlank())
            }
        )
    }

    @Test
    fun `aei log without active session`() {
        var pendingUserSessionId: String? = null
        testRule.runTest(
            setupAction = { setupFakeAeiData(listOf(anr.toAeiObject())) },
            testCaseAction = {
                drainAeiInstrumentation()
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

    private fun drainAeiInstrumentation() {
        val executor = testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)
        repeat(2) {
            executor.runCurrentlyBlocked()
        }
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
