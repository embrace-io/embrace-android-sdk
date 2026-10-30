package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.arch.modelSdkConfigClass
import io.embrace.bytecode.config.arch.stringMethod
import io.embrace.bytecode.config.model.VariantConfig
import io.embrace.bytecode.config.model.VariantOutputInfo

fun createProjectConfigInstrumentation(cfg: VariantConfig, reactNativeBundleId: String?, variantOutputInfo: VariantOutputInfo) =
    modelSdkConfigClass {
        stringMethod("getAppId") { cfg.embraceConfig?.appId }
        stringMethod("getAppFramework") { cfg.embraceConfig?.sdkConfig?.appFramework }
        stringMethod("getBuildId") { cfg.buildId }
        stringMethod("getBuildFlavor") { cfg.buildFlavor }
        stringMethod("getBuildType") { cfg.buildType }
        stringMethod("getReactNativeBundleId") { reactNativeBundleId }
        stringMethod("getVersionName") { variantOutputInfo.versionName }
        stringMethod("getVersionCode") { variantOutputInfo.versionCode }
        stringMethod("getPackageName") { variantOutputInfo.packageName }
    }
