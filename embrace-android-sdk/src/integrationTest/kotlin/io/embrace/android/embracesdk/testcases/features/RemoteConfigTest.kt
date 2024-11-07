package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
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

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `SDK can start`() {
        testRule.runTest(
            remoteConfig = RemoteConfig(100),
            preSdkStartAction = {
                assertFalse(embrace.isStarted)
            },
            testCaseAction = {
                assertTrue(embrace.isStarted)
            }
        )
    }

    @Test
    fun `SDK disabled via config cannot start`() {
        testRule.runTest(
            remoteConfig = RemoteConfig(0),
            expectSdkToStart = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
            }
        )
    }
}
