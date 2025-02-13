package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringListMethod
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createSessionConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    stringListMethod("getSessionComponents") { cfg.embraceConfig?.sdkConfig?.sessionConfig?.sessionComponents }
    stringListMethod("getFullSessionEvents") { cfg.embraceConfig?.sdkConfig?.sessionConfig?.fullSessionEvents }
}
