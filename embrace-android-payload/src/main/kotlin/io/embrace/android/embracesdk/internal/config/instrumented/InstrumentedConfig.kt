package io.embrace.android.embracesdk.internal.config.instrumented

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
object InstrumentedConfig {
    val baseUrls: BaseUrlConfig = BaseUrlConfig
    val backgroundActivity: BackgroundActivityConfig = BackgroundActivityConfig
    val enabledFeatures: EnabledFeatureConfig = EnabledFeatureConfig
    val networkCapture: NetworkCaptureConfig = NetworkCaptureConfig
    val project: ProjectConfig = ProjectConfig
    val redaction: RedactionConfig = RedactionConfig
    val session: SessionConfig = SessionConfig
}
