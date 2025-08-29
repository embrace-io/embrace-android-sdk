package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringMethod
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig

fun createProjectConfigInstrumentation(cfg: VariantConfig, reactNativeBundleId: String?) = modelSdkConfigClass {
    stringMethod("getAppId") { cfg.embraceConfig?.appId }
    stringMethod("getAppFramework") { cfg.embraceConfig?.sdkConfig?.appFramework }
    stringMethod("getBuildId") { cfg.buildId }
    stringMethod("getBuildFlavor") { cfg.buildFlavor }
    stringMethod("getBuildType") { cfg.buildType }
    stringMethod("getReactNativeBundleId") { reactNativeBundleId }
    stringMethod("getAppVersionName") { cfg.variantVersion }
    stringMethod("getAppVersionCode") { cfg.variantVersionCode?.toString() }
}
