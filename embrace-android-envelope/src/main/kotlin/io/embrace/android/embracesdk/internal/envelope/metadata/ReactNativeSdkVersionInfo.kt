package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

class ReactNativeSdkVersionInfo(
    private val impl: KeyValueStore
) : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = impl.getString(REACT_NATIVE_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(REACT_NATIVE_SDK_VERSION_KEY, value) }

    override var javaScriptPatchNumber: String?
        get() = impl.getString(JAVA_SCRIPT_PATCH_NUMBER_KEY)
        set(value) = impl.edit { putString(JAVA_SCRIPT_PATCH_NUMBER_KEY, value) }

    override var hostedPlatformVersion: String?
        get() = impl.getString(REACT_NATIVE_VERSION_KEY)
        set(value) = impl.edit { putString(REACT_NATIVE_VERSION_KEY, value) }

    private companion object {
        private const val JAVA_SCRIPT_PATCH_NUMBER_KEY = "io.embrace.javascript.patch"
        private const val REACT_NATIVE_VERSION_KEY = "io.embrace.reactnative.version"
        private const val REACT_NATIVE_SDK_VERSION_KEY = "io.embrace.reactnative.sdk.version"
    }
}
