package io.embrace.android.embracesdk.internal.capture.metadata

interface RnBundleIdTracker {

    /**
     * Sets React Native Bundle ID from a custom JavaScript Bundle URL.
     * @param jsBundleUrl the JavaScript bundle URL
     * @param forceUpdate if the bundle was updated and we need to recompute the bundleId
     */
    fun setReactNativeBundleId(jsBundleUrl: String?, forceUpdate: Boolean? = null)

    fun getReactNativeBundleId(): String?
}
