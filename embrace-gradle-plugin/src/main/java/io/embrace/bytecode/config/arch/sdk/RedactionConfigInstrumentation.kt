package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.arch.modelSdkConfigClass
import io.embrace.bytecode.config.arch.stringListMethod
import io.embrace.bytecode.config.model.VariantConfig

fun createRedactionConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    stringListMethod("getSensitiveKeysDenylist") { cfg.embraceConfig?.sdkConfig?.sensitiveKeysDenylist }
}
