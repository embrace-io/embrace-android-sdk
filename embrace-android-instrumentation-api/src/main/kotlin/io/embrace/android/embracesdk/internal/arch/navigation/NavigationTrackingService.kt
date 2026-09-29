package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Service where navigation controllers can be registered, and when they fire events, they will be dispatched to the listeners.
 */
interface NavigationTrackingService : NavigationTrackingInitListener, NavigationControllerEventListener {
    /**
     * Register listener that receives events related to the initialization of components that control navigation
     */
    var navigationTrackingInitListener: NavigationTrackingInitListener

    /**
     * Register listener that receives events related to components that control navigation
     */
    var navigationControllerEventListener: NavigationControllerEventListener

    /**
     * Adds a [source] of attributes describing the screen being left.
     */
    fun addScreenAttributesSource(source: ScreenAttributesSource)

    /**
     * Writes the attributes from every registered source to [sink]; called by the instrumentation that records screen transitions.
     */
    fun collectScreenAttributes(sink: (key: String, value: String) -> Unit)
}
