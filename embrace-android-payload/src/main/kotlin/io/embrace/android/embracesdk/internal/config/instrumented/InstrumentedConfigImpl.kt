package io.embrace.android.embracesdk.internal.config.instrumented

import io.embrace.android.embracesdk.internal.config.instrumented.schema.Base64SharedObjectFilesMap
import io.embrace.android.embracesdk.internal.config.instrumented.schema.BaseUrlConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.NetworkCaptureConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.ProjectConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig

/**
 * This class and its contents are instrumented by the embrace gradle plugin to alter its return values
 * based on what values have been set in the embrace-config.json. If no value has been set,
 * the default value specified in the class will be used.
 *
 * It's important to:
 *
 * (1) always use functions, as this is somewhat easier to instrument than Kotlin properties
 * (2) always keep the embrace gradle plugin in sync when adding new config values or altering existing ones
 */
@EmbraceInstrumented
object InstrumentedConfigImpl : InstrumentedConfig {
    override val baseUrls: BaseUrlConfig = BaseUrlConfigImpl
    override val enabledFeatures: EnabledFeatureConfig = EnabledFeatureConfigImpl
    override val networkCapture: NetworkCaptureConfig = NetworkCaptureConfigImpl
    override val otelLimits: OtelLimitsConfig = OtelLimitsConfigImpl
    override val project: ProjectConfig = ProjectConfigImpl
    override val redaction: RedactionConfig = RedactionConfigImpl
    override val symbols: Base64SharedObjectFilesMap = Base64SharedObjectFilesMapImpl
}

@EmbraceInstrumented
object BaseUrlConfigImpl : BaseUrlConfig

@EmbraceInstrumented
object EnabledFeatureConfigImpl : EnabledFeatureConfig

@EmbraceInstrumented
object NetworkCaptureConfigImpl : NetworkCaptureConfig

@EmbraceInstrumented
object OtelLimitsConfigImpl : OtelLimitsConfig

@EmbraceInstrumented
object ProjectConfigImpl : ProjectConfig

@EmbraceInstrumented
object RedactionConfigImpl : RedactionConfig

@EmbraceInstrumented
object Base64SharedObjectFilesMapImpl : Base64SharedObjectFilesMap
