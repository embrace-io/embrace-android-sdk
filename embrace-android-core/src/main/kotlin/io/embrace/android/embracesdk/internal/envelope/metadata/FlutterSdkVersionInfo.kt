package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

class FlutterSdkVersionInfo(
    private val impl: KeyValueStore,
) : HostedSdkVersionInfo {

    override var hostedPlatformVersion: String?
        get() = impl.getString(DART_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(DART_SDK_VERSION_KEY, value) }

    override var hostedSdkVersion: String?
        get() = impl.getString(EMBRACE_FLUTTER_SDK_VERSION_KEY)
        set(value) = impl.edit { putString(EMBRACE_FLUTTER_SDK_VERSION_KEY, value) }

    private companion object {
        private const val DART_SDK_VERSION_KEY = "io.embrace.dart.sdk.version"
        private const val EMBRACE_FLUTTER_SDK_VERSION_KEY = "io.embrace.flutter.sdk.version"
    }
}
