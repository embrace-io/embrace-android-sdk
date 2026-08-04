package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior.Companion.DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART
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
            assertEquals(DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART, getMaxSpanEventsPerSessionPart())
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

        with(createOtelBehavior(remoteCfg = RemoteConfig(dataConfig = DataRemoteConfig(maxSpanEventsPerSessionPart = 25)))) {
            assertEquals(25, getMaxSpanEventsPerSessionPart())
        }
    }
}
