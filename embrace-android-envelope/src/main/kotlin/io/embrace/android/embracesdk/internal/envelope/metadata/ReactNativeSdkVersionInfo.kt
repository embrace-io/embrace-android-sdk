package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

class ReactNativeSdkVersionInfo(
    impl: KeyValueStore,
) : SdkVersionInfo(impl, REACT_NATIVE_SDK_VERSION_KEY, REACT_NATIVE_VERSION_KEY) {

    override var javaScriptPatchNumber: String?
        get() = impl.getString(JAVA_SCRIPT_PATCH_NUMBER_KEY)
        set(value) = impl.editAndCommit { putString(JAVA_SCRIPT_PATCH_NUMBER_KEY, value) }

    private companion object {
        private const val JAVA_SCRIPT_PATCH_NUMBER_KEY = "io.embrace.javascript.patch"
        private const val REACT_NATIVE_VERSION_KEY = "io.embrace.reactnative.version"
        private const val REACT_NATIVE_SDK_VERSION_KEY = "io.embrace.reactnative.sdk.version"
    }
}
