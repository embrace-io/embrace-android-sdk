package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Service where navigation controllers can be registered, and when they fire events, they will be dispatched to the listeners.
 */
interface NavigationTrackingService : NavigationTrackingInitListener, NavigationControllerEventListener {
    /**
     * Register listener that receives events related to the initialization of components that control navigation
     */
    fun setTrackingInitListener(listener: NavigationTrackingInitListener)

    /**
     * Register listener that receives events related to components that control navigation
     */
    fun setControllerEventListener(listener: NavigationControllerEventListener)
}
