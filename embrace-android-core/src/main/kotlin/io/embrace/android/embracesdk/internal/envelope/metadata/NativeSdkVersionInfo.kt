package io.embrace.android.embracesdk.internal.envelope.metadata

internal class NativeSdkVersionInfo : HostedSdkVersionInfo {

    override var hostedSdkVersion: String?
        get() = null
        set(value) {}

    override var hostedPlatformVersion: String?
        get() = null
        set(value) {}
}
