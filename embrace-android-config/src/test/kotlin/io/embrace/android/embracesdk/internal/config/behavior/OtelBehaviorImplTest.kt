package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelLimitsRemoteConfig
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
    fun testOtelLimits() {
        with(createOtelBehavior()) {
            assertEquals(OtelLimitsConfigImpl.getMaxNameLength(), otelLimits.getMaxNameLength())
        }

        val remoteCfg = RemoteConfig(otelLimitsConfig = OtelLimitsRemoteConfig(maxNameLength = 42))
        with(createOtelBehavior(remoteCfg = remoteCfg)) {
            assertEquals(42, otelLimits.getMaxNameLength())
            assertEquals(OtelLimitsConfigImpl.getMaxCustomEventCount(), otelLimits.getMaxCustomEventCount())
        }
    }
}
