package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Service where a navigation source can be registered so that the SDK can ask it to look for a controller on an Activity.
 */
interface NavigationTrackingService : NavigationTrackingInitListener {
    /**
     * Register listener that receives events related to the initialization of components that control navigation
     */
    var navigationTrackingInitListener: NavigationTrackingInitListener
}
