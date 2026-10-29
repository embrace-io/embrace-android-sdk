package io.embrace.bytecode.config.arch.sdk

import io.embrace.bytecode.config.arch.modelSdkConfigClass
import io.embrace.bytecode.config.arch.stringMethod

fun createSharedObjectFilesMapInstrumentation(encodedSharedObjectFilesMap: String?) = modelSdkConfigClass {
    stringMethod("getBase64SharedObjectFilesMap") { encodedSharedObjectFilesMap }
}
