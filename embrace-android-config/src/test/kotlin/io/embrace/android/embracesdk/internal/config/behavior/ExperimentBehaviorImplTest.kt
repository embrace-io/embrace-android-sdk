package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createExperimentBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExperimentBehaviorImplTest {

    @Test
    fun `defaults when remote config is null`() {
        with(createExperimentBehavior()) {
            assertEquals(500, getMaxExperimentCount())
            assertEquals(128, getMaxIdLength())
            assertEquals(128, getMaxVariantLength())
        }
    }

    @Test
    fun `defaults when remote config fields are null`() {
        with(createExperimentBehavior(remoteCfg = RemoteConfig())) {
            assertEquals(500, getMaxExperimentCount())
            assertEquals(128, getMaxIdLength())
            assertEquals(128, getMaxVariantLength())
        }
    }

    @Test
    fun `remote overrides are respected`() {
        val cfg = RemoteConfig(
            experimentMaxCount = 250,
            experimentIdMaxLength = 64,
            experimentVariantMaxLength = 32,
        )
        with(createExperimentBehavior(remoteCfg = cfg)) {
            assertEquals(250, getMaxExperimentCount())
            assertEquals(64, getMaxIdLength())
            assertEquals(32, getMaxVariantLength())
        }
    }

    @Test
    fun `max experiment count is capped at 5000 when remote value exceeds it`() {
        val cfg = RemoteConfig(experimentMaxCount = 5001)
        assertEquals(5000, createExperimentBehavior(remoteCfg = cfg).getMaxExperimentCount())
    }

    @Test
    fun `max experiment count is valid at exactly 5000`() {
        val cfg = RemoteConfig(experimentMaxCount = 5000)
        assertEquals(5000, createExperimentBehavior(remoteCfg = cfg).getMaxExperimentCount())
    }

    @Test
    fun `id and variant lengths are capped at 1024 when remote values exceed it`() {
        val cfg = RemoteConfig(
            experimentIdMaxLength = 1025,
            experimentVariantMaxLength = 1025,
        )
        with(createExperimentBehavior(remoteCfg = cfg)) {
            assertEquals(1024, getMaxIdLength())
            assertEquals(1024, getMaxVariantLength())
        }
    }

    @Test
    fun `id and variant lengths are valid at exactly 1024`() {
        val cfg = RemoteConfig(
            experimentIdMaxLength = 1024,
            experimentVariantMaxLength = 1024,
        )
        with(createExperimentBehavior(remoteCfg = cfg)) {
            assertEquals(1024, getMaxIdLength())
            assertEquals(1024, getMaxVariantLength())
        }
    }
}
