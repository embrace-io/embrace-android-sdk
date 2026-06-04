package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeNetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TraceparentInjectionBehaviorImplTest {

    private val thresholdCheck = BehaviorThresholdCheck { FAKE_DEVICE_ID }

    @Test
    fun `feature off by default`() {
        val local = FakeInstrumentedConfig()
        val behavior = TraceparentInjectionBehaviorImpl(
            thresholdCheck = thresholdCheck,
            local = local,
            remote = null
        )
        with(behavior) {
            assertFalse(isTraceparentInjectionEnabled())
            assertFalse(shouldInjectTraceparent("test.com"))
        }
    }

    @Test
    fun `local flag works`() {
        assertTrue(behavior(localInjectFlagEnabled = true).isTraceparentInjectionEnabled())
        assertTrue(behavior(localInjectFlagEnabled = true).shouldInjectTraceparent("test.com"))
        assertFalse(behavior(localInjectFlagEnabled = false).isTraceparentInjectionEnabled())
        assertFalse(behavior(localInjectFlagEnabled = false).shouldInjectTraceparent("test.com"))
    }

    @Test
    fun `remote flag works and overrides local`() {
        assertTrue(behavior(remote = remote(injectionPct = 100f)).isTraceparentInjectionEnabled())
        assertTrue(behavior(remote = remote(injectionPct = 100f)).shouldInjectTraceparent("test.com"))
        assertFalse(behavior(remote = remote(injectionPct = 0f)).isTraceparentInjectionEnabled())
        assertFalse(behavior(remote = remote(injectionPct = 0f)).shouldInjectTraceparent("test.com"))
        assertTrue(behavior(localInjectFlagEnabled = false, remote = remote(injectionPct = 100f)).isTraceparentInjectionEnabled())
        assertFalse(behavior(localInjectFlagEnabled = true, remote = remote(injectionPct = 0f)).isTraceparentInjectionEnabled())
    }

    @Test
    fun `shouldInjectTraceparent works as expected given different allowLists`() {
        assertTrue(
            behavior(
                allowedDomains = null,
            ).shouldInjectTraceparent("test.com")
        )

        assertFalse(
            behavior(
                allowedDomains = emptyList(),
            ).shouldInjectTraceparent("test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf("test.com"),
            ).shouldInjectTraceparent("test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf(".test.com"),
            ).shouldInjectTraceparent("foo.test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf(".TEST.com"),
            ).shouldInjectTraceparent("foo.test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf(".test.com"),
            ).shouldInjectTraceparent("foo.TEST.com")
        )

        assertFalse(
            behavior(
                allowedDomains = listOf("test.com"),
            ).shouldInjectTraceparent(null)
        )

        assertFalse(
            behavior(
                allowedDomains = listOf("test.com"),
            ).shouldInjectTraceparent("foo.com")
        )
    }

    private fun remote(injectionPct: Float? = null) = RemoteConfig(traceparentInjectionPctEnabled = injectionPct)

    private fun behavior(
        localInjectFlagEnabled: Boolean = true,
        allowedDomains: List<String>? = null,
        remote: RemoteConfig? = null,
    ): TraceparentInjectionBehaviorImpl {
        val local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(traceparentInjection = localInjectFlagEnabled),
            networkCapture = FakeNetworkCaptureConfig(traceparentOnlyAllowDomains = allowedDomains),
        )
        return TraceparentInjectionBehaviorImpl(thresholdCheck, local, remote)
    }
}
