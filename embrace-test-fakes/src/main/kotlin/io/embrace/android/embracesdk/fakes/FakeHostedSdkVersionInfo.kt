package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo

class FakeHostedSdkVersionInfo : HostedSdkVersionInfo {
    override var hostedSdkVersion: String? = null
    override var hostedPlatformVersion: String? = null
}
