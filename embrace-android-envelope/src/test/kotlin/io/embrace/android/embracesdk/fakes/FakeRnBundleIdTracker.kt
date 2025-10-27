package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker

class FakeRnBundleIdTracker : RnBundleIdTracker {

    var fakeReactNativeBundleId: String? = "fakeReactNativeBundleId"
    var forceUpdate: Boolean? = null

    override fun setReactNativeBundleId(jsBundleUrl: String?, forceUpdate: Boolean?) {
        fakeReactNativeBundleId = jsBundleUrl
        this.forceUpdate = forceUpdate
    }

    override fun getReactNativeBundleId(): String? {
        return fakeReactNativeBundleId
    }
}
