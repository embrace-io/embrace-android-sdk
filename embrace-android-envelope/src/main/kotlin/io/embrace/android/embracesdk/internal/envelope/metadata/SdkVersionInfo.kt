package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.store.KeyValueStore

abstract class SdkVersionInfo(
    protected val impl: KeyValueStore,
    private val sdkVersionKey: String,
    private val platformVersionKey: String,
) : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = impl.getString(sdkVersionKey)
        set(value) = impl.editAndCommit { putString(sdkVersionKey, value) }

    override var hostedPlatformVersion: String?
        get() = impl.getString(platformVersionKey)
        set(value) = impl.editAndCommit { putString(platformVersionKey, value) }
}
