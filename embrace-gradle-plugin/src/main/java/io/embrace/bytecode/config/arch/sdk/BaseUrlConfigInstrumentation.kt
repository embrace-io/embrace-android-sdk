package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.arch.modelSdkConfigClass
import io.embrace.bytecode.config.arch.stringMethod
import io.embrace.bytecode.config.model.VariantConfig

fun createBaseUrlConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    stringMethod("getConfig") { cfg.embraceConfig?.sdkConfig?.baseUrls?.config }
    stringMethod("getData") { cfg.embraceConfig?.sdkConfig?.baseUrls?.data }
}
