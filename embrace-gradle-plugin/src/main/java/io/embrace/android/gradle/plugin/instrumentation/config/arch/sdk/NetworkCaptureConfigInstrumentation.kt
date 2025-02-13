package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.intMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.mapMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringListMethod
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringMethod
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createNetworkCaptureConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    intMethod("getRequestLimitPerDomain") { cfg.embraceConfig?.sdkConfig?.networking?.defaultCaptureLimit }
    stringListMethod("getIgnoredRequestPatternList") { cfg.embraceConfig?.sdkConfig?.networking?.disabledUrlPatterns }
    stringMethod("getNetworkBodyCapturePublicKey") { cfg.embraceConfig?.sdkConfig?.capturePublicKey }
    mapMethod("getLimitsByDomain") {
        cfg.embraceConfig?.sdkConfig?.networking?.domains?.associate {
            it.domain to it.limit.toString()
        }
    }
}
