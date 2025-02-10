package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests how the SDK behaves when controlling by 'remote config'. This is a HTTP response from the
 * Embrace server that can enable or disable individual features or the entire SDK.
 */
@RunWith(AndroidJUnit4::class)
internal class RemoteConfigTest {

    private val sdkDisabledConfig = RemoteConfig(0)
    private val sdkEnabledConfig = RemoteConfig(100)

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `SDK can start`() {
        val module by lazy { testRule.bootstrapper.workerThreadModule }
        testRule.runTest(persistedRemoteConfig = sdkEnabledConfig,
            serverResponseConfig = sdkDisabledConfig,
            preSdkStartAction = {
                assertNoConfigPersisted()
                assertFalse(embrace.isStarted)
            },
            testCaseAction = {
                assertTrue(embrace.isStarted)
            },
            assertAction = {
                assertConfigStored(module, 0)
            })
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        val module by lazy { testRule.bootstrapper.workerThreadModule }
        testRule.runTest(persistedRemoteConfig = sdkDisabledConfig,
            serverResponseConfig = sdkEnabledConfig,
            expectSdkToStart = false,
            preSdkStartAction = {
                assertNoConfigPersisted()
            },
            testCaseAction = {
                assertFalse(embrace.isStarted)
            },
            assertAction = {
                assertConfigRequested(1)
                assertConfigStored(module, 100)
            })
    }

    private fun EmbracePayloadAssertionInterface.assertConfigStored(
        module: WorkerThreadModule,
        expectedThreshold: Int,
    ) {
        assertConfigRequested(1)
        module.backgroundWorker(Worker.Background.IoRegWorker).shutdownAndWait(10000)
        val response = readPersistedConfigResponse()
        assertEquals(expectedThreshold, response.cfg?.threshold)
        assertEquals("server_etag_value", response.etag)
    }
}
