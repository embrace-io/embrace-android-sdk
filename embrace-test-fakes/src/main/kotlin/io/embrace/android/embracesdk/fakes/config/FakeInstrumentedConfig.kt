package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.NetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.ProjectConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.SessionConfig

/**
 * A fake [InstrumentedConfig] implementation that defaults to the real implementation unless an override is supplied.
 */
data class FakeInstrumentedConfig(
    private val base: InstrumentedConfig = InstrumentedConfigImpl,
    override val baseUrls: FakeBaseUrlConfig = FakeBaseUrlConfig(base.baseUrls),
    override val enabledFeatures: FakeEnabledFeatureConfig = FakeEnabledFeatureConfig(base.enabledFeatures),
    override val networkCapture: NetworkCaptureConfig = FakeNetworkCaptureConfig(base.networkCapture),
    override val otelLimits: OtelLimitsConfig = FakeOtelLimitsConfig(),
    override val project: ProjectConfig = FakeProjectConfig(base.project, appId = "abcde"),
    override val redaction: RedactionConfig = FakeRedactionConfig(base.redaction),
    override val session: SessionConfig = FakeSessionConfig(base.session),
) : InstrumentedConfig
