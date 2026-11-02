package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

class FlutterSdkVersionInfo(
    impl: KeyValueStore,
) : SdkVersionInfo(impl, EMBRACE_FLUTTER_SDK_VERSION_KEY, DART_SDK_VERSION_KEY) {

    private companion object {
        private const val DART_SDK_VERSION_KEY = "io.embrace.dart.sdk.version"
        private const val EMBRACE_FLUTTER_SDK_VERSION_KEY = "io.embrace.flutter.sdk.version"
    }
}
