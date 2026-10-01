package io.embrace.android.embracesdk.testcases.session

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class UserSessionLastActivityUpdateTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.NonIoRegWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }
    }

    @Test
    fun `foreground task updates user session last activity time`() {
        lateinit var store: KeyValueStore
        lateinit var worker: BlockingScheduledExecutorService
        val inactivityTimeoutSeconds = 60
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds)
            ),
            setupAction = {
                store = getStore()
                worker = getFakedWorkerExecutor(Worker.Background.NonIoRegWorker)
            },
            testCaseAction = {
                recordSession {
                    val initialLastActivityMs = store.currentUserSessionLastActivityTimestamp()
                    clock.tick(30_000)
                    worker.runCurrentlyBlocked()
                    assertTrue(store.currentUserSessionLastActivityTimestamp() > initialLastActivityMs)
                }
            },
        )
    }

    private fun KeyValueStore.currentUserSessionLastActivityTimestamp(): Long =
        checkNotNull(getStringMap("embrace.user_session")?.get("emb.user_session_last_activity_ts")).toLong()

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
