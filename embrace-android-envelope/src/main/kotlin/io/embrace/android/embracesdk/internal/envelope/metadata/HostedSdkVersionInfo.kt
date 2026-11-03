package io.embrace.android.embracesdk.internal.envelope.metadata

// Temporary until we confirm we can use just one preference for sdk version
interface HostedSdkVersionInfo {
    val hostedSdkVersion: String?
    val hostedPlatformVersion: String?
    val unityBuildIdNumber: String?
    val javaScriptPatchNumber: String?

    /**
     * Applies every change [action] makes to the editor as a single write.
     */
    fun update(action: HostedSdkVersionEditor.() -> Unit)
}
