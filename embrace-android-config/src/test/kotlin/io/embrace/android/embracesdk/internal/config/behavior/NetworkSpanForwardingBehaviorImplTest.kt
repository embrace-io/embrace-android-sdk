@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeNetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkSpanForwardingBehaviorImplTest {

    private val thresholdCheck = BehaviorThresholdCheck { FAKE_DEVICE_ID }

    @Test
    fun `feature off by default`() {
        val local = FakeInstrumentedConfig()
        val behavior =
            NetworkSpanForwardingBehaviorImpl(
                traceparentInjectionBehavior =
                TraceparentInjectionBehaviorImpl(
                    thresholdCheck = thresholdCheck,
                    local = local,
                    remote = null
                ),
                thresholdCheck = thresholdCheck,
                local = local,
                remote = null
            )
        assertFalse(behavior.isNetworkSpanForwardingEnabled())
        assertFalse(behavior.shouldForwardForDomain("anything.com"))
    }

    @Test
    fun `local flag works`() {
        assertTrue(behavior(localNsfEnabled = true).isNetworkSpanForwardingEnabled())
        assertFalse(behavior(localNsfEnabled = false).isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `remote flag works and overrides local`() {
        assertTrue(behavior(remote = remote(nsfEnabledPct = 100f)).isNetworkSpanForwardingEnabled())
        assertTrue(behavior(localNsfEnabled = false, remote = remote(nsfEnabledPct = 100f)).isNetworkSpanForwardingEnabled())
        assertFalse(behavior(localNsfEnabled = true, remote = remote(nsfEnabledPct = 0f)).isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `NSF is on only if traceparent injection is on`() {
        assertFalse(behavior(localInjectionEnabled = false, localNsfEnabled = true).isNetworkSpanForwardingEnabled())
        assertFalse(behavior(localInjectionEnabled = false, remote = remote(nsfEnabledPct = 100f)).isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `deprecated remote flag only used if non-deprecated one isn't defined`() {
        assertTrue(behavior(remote = remote(deprecatedNsfEnabledPct = 100f)).isNetworkSpanForwardingEnabled())
        assertTrue(behavior(remote = remote(nsfEnabledPct = 100f, deprecatedNsfEnabledPct = 0f)).isNetworkSpanForwardingEnabled())
        assertFalse(behavior(remote = remote(nsfEnabledPct = 0f, deprecatedNsfEnabledPct = 100f)).isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `shouldForwardForDomain requires NSF, injection, and an allowed host`() {
        assertTrue(
            behavior(
                allowedDomains = null,
                localNsfEnabled = true
            ).shouldForwardForDomain("test.com")
        )

        assertFalse(
            behavior(
                localInjectionEnabled = false,
                allowedDomains = null,
                localNsfEnabled = true
            ).shouldForwardForDomain("test.com")
        )

        assertFalse(
            behavior(
                allowedDomains = emptyList(),
                localNsfEnabled = true
            ).shouldForwardForDomain("test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf("test.com"),
                localNsfEnabled = true
            ).shouldForwardForDomain("test.com")
        )

        assertTrue(
            behavior(
                allowedDomains = listOf(".test.com"),
                localNsfEnabled = true
            ).shouldForwardForDomain("foo.test.com")
        )

        assertFalse(
            behavior(
                allowedDomains = listOf("test.com"),
                localNsfEnabled = true
            ).shouldForwardForDomain("foo.com")
        )
    }

    @Test
    fun `legacy fallback forwards for all hosts regardless of allowlist or injection flag`() {
        val behavior = behavior(
            localInjectionEnabled = false,
            allowedDomains = emptyList(),
            remote = remote(deprecatedNsfEnabledPct = 100f),
        )
        assertTrue(behavior.isNetworkSpanForwardingEnabled())
        assertTrue(behavior.shouldForwardForDomain("test.com"))
    }

    @Test
    fun `legacy fallback does not apply when the flat NSF flag is present`() {
        val behavior = behavior(
            localInjectionEnabled = false,
            allowedDomains = listOf("test.com"),
            remote = remote(nsfEnabledPct = 100f, deprecatedNsfEnabledPct = 100f),
        )
        assertFalse(behavior.isNetworkSpanForwardingEnabled())
        assertFalse(behavior.shouldForwardForDomain("test.com"))
    }

    private fun remote(
        nsfEnabledPct: Float? = null,
        deprecatedNsfEnabledPct: Float? = null,
        injectionEnabledPct: Float? = null,
    ) = RemoteConfig(
        nsfPctEnabled = nsfEnabledPct,
        traceparentInjectionPctEnabled = injectionEnabledPct,
        networkSpanForwardingRemoteConfig = deprecatedNsfEnabledPct?.let { NetworkSpanForwardingRemoteConfig(pctEnabled = it) },
    )

    private fun behavior(
        localInjectionEnabled: Boolean = true,
        localNsfEnabled: Boolean = false,
        allowedDomains: List<String>? = null,
        remote: RemoteConfig? = null,
    ): NetworkSpanForwardingBehaviorImpl {
        val local: InstrumentedConfig = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                networkSpanForwarding = localNsfEnabled,
                traceparentInjection = localInjectionEnabled,
            ),
            networkCapture = FakeNetworkCaptureConfig(traceparentOnlyAllowDomains = allowedDomains),
        )
        return NetworkSpanForwardingBehaviorImpl(
            traceparentInjectionBehavior =
            TraceparentInjectionBehaviorImpl(
                thresholdCheck = thresholdCheck,
                local = local,
                remote = remote
            ),
            thresholdCheck = thresholdCheck,
            local = local,
            remote = remote
        )
    }
}
