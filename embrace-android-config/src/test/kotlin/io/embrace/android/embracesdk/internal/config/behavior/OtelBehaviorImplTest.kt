package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OtelBehaviorImplTest {

    @Test
    fun testDefault() {
        with(OtelBehaviorImpl(FakeInstrumentedConfig())) {
            assertFalse(shouldUseKotlinSdk())
        }
    }

    @Test
    fun testLocalEnabled() {
        val config = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(otelKotlinSdkEnabled = true),
        )
        with(OtelBehaviorImpl(config)) {
            assertTrue(shouldUseKotlinSdk())
        }
    }

    @Test
    fun testLocalDisabled() {
        val config = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(otelKotlinSdkEnabled = false),
        )
        with(OtelBehaviorImpl(config)) {
            assertFalse(shouldUseKotlinSdk())
        }
    }
}
