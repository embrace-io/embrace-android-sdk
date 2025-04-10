package io.embrace.android.gradle.plugin.instrumentation.config.arch.sdk

import io.embrace.android.gradle.plugin.instrumentation.config.arch.modelSdkConfigClass
import io.embrace.android.gradle.plugin.instrumentation.config.arch.stringMethod

fun createSharedObjectFilesMapInstrumentation(encodedSharedObjectFilesMap: String?) = modelSdkConfigClass {
    stringMethod("getBase64SharedObjectFilesMap") { encodedSharedObjectFilesMap }
}
