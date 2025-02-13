package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringListMethod
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createRedactionConfigInstrumentation(cfg: VariantConfig) = modelSdkConfigClass {
    stringListMethod("getSensitiveKeysDenylist") { cfg.embraceConfig?.sdkConfig?.sensitiveKeysDenylist }
}
