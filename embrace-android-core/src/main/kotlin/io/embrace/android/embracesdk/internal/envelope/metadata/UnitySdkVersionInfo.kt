package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

class UnitySdkVersionInfo(
    private val impl: KeyValueStore,
) : HostedSdkVersionInfo {

    override var hostedPlatformVersion: String?
        get() = impl.getString(UNITY_VERSION_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_VERSION_NUMBER_KEY, value) }

    override var unityBuildIdNumber: String?
        get() = impl.getString(UNITY_BUILD_ID_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_BUILD_ID_NUMBER_KEY, value) }

    override var hostedSdkVersion: String?
        get() = impl.getString(UNITY_SDK_VERSION_NUMBER_KEY)
        set(value) = impl.edit { putString(UNITY_SDK_VERSION_NUMBER_KEY, value) }

    private companion object {
        private const val UNITY_VERSION_NUMBER_KEY = "io.embrace.unity.version"
        private const val UNITY_BUILD_ID_NUMBER_KEY = "io.embrace.unity.build.id"
        private const val UNITY_SDK_VERSION_NUMBER_KEY = "io.embrace.unity.sdk.version"
    }
}
