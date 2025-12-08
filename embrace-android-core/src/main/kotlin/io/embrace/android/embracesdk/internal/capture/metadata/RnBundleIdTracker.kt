package io.embrace.android.embracesdk.internal.capture.metadata

interface RnBundleIdTracker {

    /**
     * Sets React Native Bundle ID from a custom JavaScript Bundle URL.
     * @param jsBundleUrl the JavaScript bundle URL
     */
    fun setReactNativeBundleId(jsBundleUrl: String?, forceUpdate: Boolean? = null)

    fun getReactNativeBundleId(): String?
}
