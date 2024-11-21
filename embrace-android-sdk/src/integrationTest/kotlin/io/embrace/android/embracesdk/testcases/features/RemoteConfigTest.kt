package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
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
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `SDK can start`() {
        testRule.runTest(
            persistedRemoteConfig = sdkEnabledConfig,
            preSdkStartAction = {
                assertNoConfigPersisted()
                assertFalse(embrace.isStarted)
            },
            testCaseAction = {
                assertTrue(embrace.isStarted)
            },
            assertAction = {
                assertConfigRequested(1)
                val response = readPersistedConfigResponse()
                assertEquals(100, response.cfg?.threshold)
                assertEquals("server_etag_value", response.etag)
            }
        )
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        testRule.runTest(
            persistedRemoteConfig = sdkDisabledConfig,
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
                returnIfConditionMet(
                    waitTimeMs = 10000,
                    dataProvider = ::readPersistedConfigResponse,
                    condition = { response ->
                        response.cfg?.threshold == 100 && response.etag == "server_etag_value"
                    },
                    desiredValueSupplier = {}
                )
            }
        )
    }
}
