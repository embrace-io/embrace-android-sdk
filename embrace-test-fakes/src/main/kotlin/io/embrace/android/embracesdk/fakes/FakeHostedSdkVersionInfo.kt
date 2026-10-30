package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionEditor
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo

class FakeHostedSdkVersionInfo : HostedSdkVersionInfo {
    override var hostedSdkVersion: String? = null
    override var hostedPlatformVersion: String? = null
    override var unityBuildIdNumber: String? = null
    override var javaScriptPatchNumber: String? = null

    override fun update(action: HostedSdkVersionEditor.() -> Unit) {
        val editor = HostedSdkVersionEditor(hostedSdkVersion, hostedPlatformVersion, unityBuildIdNumber, javaScriptPatchNumber)
            .apply(action)
        hostedSdkVersion = editor.hostedSdkVersion
        hostedPlatformVersion = editor.hostedPlatformVersion
        unityBuildIdNumber = editor.unityBuildIdNumber
        javaScriptPatchNumber = editor.javaScriptPatchNumber
    }
}
