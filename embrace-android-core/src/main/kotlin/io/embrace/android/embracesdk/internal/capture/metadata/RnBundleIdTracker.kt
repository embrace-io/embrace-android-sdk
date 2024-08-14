package io.embrace.android.embracesdk.internal.capture.metadata

public interface RnBundleIdTracker {

    /**
     * Sets React Native Bundle ID from a custom JavaScript Bundle URL.
     * @param jsBundleUrl the JavaScript bundle URL
     * @param forceUpdate if the bundle was updated and we need to recompute the bundleId
     */
    public fun setReactNativeBundleId(jsBundleUrl: String?, forceUpdate: Boolean? = null)

    public fun getReactNativeBundleId(): String?
}
