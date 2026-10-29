package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.arch.intMethod
import io.embrace.bytecode.config.arch.mapMethod
import io.embrace.bytecode.config.arch.modelSdkConfigClass
import io.embrace.bytecode.config.arch.stringListMethod
import io.embrace.bytecode.config.arch.stringMethod
import io.embrace.bytecode.config.model.VariantConfig

fun createNetworkCaptureConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    intMethod("getRequestLimitPerDomain") { cfg.embraceConfig?.sdkConfig?.networking?.defaultCaptureLimit }
    stringListMethod("getIgnoredRequestPatternList") { cfg.embraceConfig?.sdkConfig?.networking?.disabledUrlPatterns }
    stringListMethod("getTraceparentOnlyAllowDomains") {
        cfg.embraceConfig?.sdkConfig?.networking?.traceparentOnlyAllowDomains
    }
    stringMethod("getNetworkBodyCapturePublicKey") { cfg.embraceConfig?.sdkConfig?.capturePublicKey }
    mapMethod("getLimitsByDomain") {
        cfg.embraceConfig?.sdkConfig?.networking?.domains?.associate {
            it.domain to it.limit.toString()
        }
    }
}
