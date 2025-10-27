package io.embrace.android.embracesdk.internal.envelope.metadata

// Temporary until we confirm we can use just one preference for sdk version
interface HostedSdkVersionInfo {
    var hostedSdkVersion: String?
    var hostedPlatformVersion: String?
    var unityBuildIdNumber: String?
        get() = null
        set(value) {}
    var javaScriptPatchNumber: String?
        get() = null
        set(value) {}
}
