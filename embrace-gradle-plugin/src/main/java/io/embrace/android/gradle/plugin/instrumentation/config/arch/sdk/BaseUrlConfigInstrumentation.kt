package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringMethod
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createBaseUrlConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    stringMethod("getConfig") { cfg.embraceConfig?.sdkConfig?.baseUrls?.config }
    stringMethod("getData") { cfg.embraceConfig?.sdkConfig?.baseUrls?.data }
}
