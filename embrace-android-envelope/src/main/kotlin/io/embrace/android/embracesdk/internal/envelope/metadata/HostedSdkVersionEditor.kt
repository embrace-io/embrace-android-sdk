package io.embrace.android.embracesdk.internal.envelope.metadata

/**
 * Holds the hosted SDK values during [HostedSdkVersionInfo.update], starting at their current values.
 */
class HostedSdkVersionEditor(
    var hostedSdkVersion: String?,
    var hostedPlatformVersion: String?,
    var unityBuildIdNumber: String?,
    var javaScriptPatchNumber: String?,
)
