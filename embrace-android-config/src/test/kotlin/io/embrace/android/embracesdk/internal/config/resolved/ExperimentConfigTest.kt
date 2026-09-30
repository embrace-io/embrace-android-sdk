package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExperimentConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().experiment
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).experiment
        with(defaults) {
            assertEquals(500, maxCount)
            assertEquals(128, maxIdLength)
            assertEquals(128, maxVariantLength)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
        assertEquals(defaults.snapshot(), resolveExperiment(RemoteConfig()).snapshot())
    }

    @Test
    fun `remote values override defaults`() {
        val remote = RemoteConfig(
            experimentMaxCount = 250,
            experimentIdMaxLength = 64,
            experimentVariantMaxLength = 32,
        )
        with(resolveExperiment(remote)) {
            assertEquals(250, maxCount)
            assertEquals(64, maxIdLength)
            assertEquals(32, maxVariantLength)
        }
    }

    @Test
    fun `values are capped at their ceiling`() {
        val atCeiling = RemoteConfig(
            experimentMaxCount = 5000,
            experimentIdMaxLength = 1024,
            experimentVariantMaxLength = 1024,
        )
        val overCeiling = RemoteConfig(
            experimentMaxCount = 5001,
            experimentIdMaxLength = 1025,
            experimentVariantMaxLength = 1025,
        )
        assertEquals(listOf(5000, 1024, 1024), resolveExperiment(atCeiling).snapshot())
        assertEquals(listOf(5000, 1024, 1024), resolveExperiment(overCeiling).snapshot())
    }

    private fun ExperimentConfig.snapshot() = listOf(maxCount, maxIdLength, maxVariantLength)
}
