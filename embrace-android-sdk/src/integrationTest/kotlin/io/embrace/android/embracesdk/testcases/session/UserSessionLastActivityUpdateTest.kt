package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionLastActivityUpdateTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(Worker.Background.PeriodicCacheWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.PeriodicCacheWorker).blockingMode = false
        }
    }

    @Test
    fun `foreground periodic cache task updates user session last activity time`() {
        lateinit var store: KeyValueStore
        lateinit var cacheWorker: BlockingScheduledExecutorService
        val inactivityTimeoutSeconds = 60
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds)
            ),
            setupAction = {
                store = getStore()
                cacheWorker = getFakedWorkerExecutor(Worker.Background.PeriodicCacheWorker)
            },
            testCaseAction = {
                recordSession {
                    val initialLastActivityMs = store.currentUserSessionLastActivityTimestamp()
                    clock.tick(90_000)
                    cacheWorker.runCurrentlyBlocked()
                    assertTrue(store.currentUserSessionLastActivityTimestamp() > initialLastActivityMs)
                }
            },
        )
    }

    private fun KeyValueStore.currentUserSessionLastActivityTimestamp(): Long =
        checkNotNull(getStringMap("embrace.user_session")?.get("emb.user_session_last_activity_ts")).toLong()
}
