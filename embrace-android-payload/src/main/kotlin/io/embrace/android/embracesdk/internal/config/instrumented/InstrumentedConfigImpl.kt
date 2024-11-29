package io.embrace.android.embracesdk.internal.config.instrumented

import io.embrace.android.embracesdk.internal.config.instrumented.schema.BaseUrlConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.NetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.ProjectConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.SessionConfig

/**
 * This class and its contents are instrumented by the swazzler to alter its return values
 * based on what values have been set in the embrace-config.json. If no value has been set,
 * the default value specified in the class will be used.
 *
 * It's important to:
 *
 * (1) always use functions, as this is somewhat easier to instrument than Kotlin properties
 * (2) always keep the swazzler in sync when adding new config values or altering existing ones
 */
@Swazzled
object InstrumentedConfigImpl : InstrumentedConfig {
    override val baseUrls: BaseUrlConfig = BaseUrlConfigImpl
    override val enabledFeatures: EnabledFeatureConfig = EnabledFeatureConfigImpl
    override val networkCapture: NetworkCaptureConfig = NetworkCaptureConfigImpl
    override val project: ProjectConfig = ProjectConfigImpl
    override val redaction: RedactionConfig = RedactionConfigImpl
    override val session: SessionConfig = SessionConfigImpl
    override val spanLimits: OtelLimitsConfig = OtelLimitsConfigImpl
}

@Swazzled
object BaseUrlConfigImpl : BaseUrlConfig

@Swazzled
object EnabledFeatureConfigImpl : EnabledFeatureConfig

@Swazzled
object NetworkCaptureConfigImpl : NetworkCaptureConfig

@Swazzled
object ProjectConfigImpl : ProjectConfig

@Swazzled
object RedactionConfigImpl : RedactionConfig

@Swazzled
object SessionConfigImpl : SessionConfig

@Swazzled
object OtelLimitsConfigImpl : OtelLimitsConfig
