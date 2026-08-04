package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OtelBehaviorImplTest {

    private val remoteEnabled = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100.0f))
    private val remoteDisabled = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0.0f))

    @Test
    fun testDefault() {
        with(createOtelBehavior()) {
            assertFalse(shouldUseKotlinSdk())
        }
    }

    @Test
    fun testRemote() {
        with(createOtelBehavior(remoteCfg = remoteEnabled)) {
            assertTrue(shouldUseKotlinSdk())
        }

        with(createOtelBehavior(remoteCfg = remoteDisabled)) {
            assertFalse(shouldUseKotlinSdk())
        }
    }

    @Test
    fun `span limits default when no remote config supplied`() {
        with(createOtelBehavior()) {
            assertEquals(500, getMaxCustomSpansPerSessionPart())
            assertEquals(1500, getMaxInternalSpansPerSessionPart())
            assertEquals(2000, getMaxNetworkSpansPerSessionPart())
        }
    }

    @Test
    fun `span limits default when remote config omits them`() {
        with(createOtelBehavior(remoteCfg = RemoteConfig(dataConfig = DataRemoteConfig()))) {
            assertEquals(500, getMaxCustomSpansPerSessionPart())
            assertEquals(1500, getMaxInternalSpansPerSessionPart())
            assertEquals(2000, getMaxNetworkSpansPerSessionPart())
        }
    }

    @Test
    fun `span limits are read from remote config`() {
        val remote = RemoteConfig(
            dataConfig = DataRemoteConfig(
                maxCustomSpansPerSession = 10,
                maxInternalSpansPerSession = 20,
                maxNetworkSpansPerSession = 30,
            ),
        )
        with(createOtelBehavior(remoteCfg = remote)) {
            assertEquals(10, getMaxCustomSpansPerSessionPart())
            assertEquals(20, getMaxInternalSpansPerSessionPart())
            assertEquals(30, getMaxNetworkSpansPerSessionPart())
        }
    }

    @Test
    fun `a remote span limit of zero is honored`() {
        val remote = RemoteConfig(
            dataConfig = DataRemoteConfig(
                maxCustomSpansPerSession = 0,
                maxInternalSpansPerSession = 0,
                maxNetworkSpansPerSession = 0,
            ),
        )
        with(createOtelBehavior(remoteCfg = remote)) {
            assertEquals(0, getMaxCustomSpansPerSessionPart())
            assertEquals(0, getMaxInternalSpansPerSessionPart())
            assertEquals(0, getMaxNetworkSpansPerSessionPart())
        }
    }
}
