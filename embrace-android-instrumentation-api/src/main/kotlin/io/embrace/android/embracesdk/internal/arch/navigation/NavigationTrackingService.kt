package io.embrace.android.embracesdk.internal.arch.navigation

/**
 * Service where a navigation source can be registered so that the SDK can ask it to look for a controller on an Activity.
 */
interface NavigationTrackingService : NavigationTrackingInitListener {
    /**
     * Register listener that receives events related to the initialization of components that control navigation
     */
    var navigationTrackingInitListener: NavigationTrackingInitListener

    /**
     * Adds a [source] of attributes describing the screen being left.
     */
    fun addScreenAttributesSource(source: ScreenAttributesSource)

    /**
     * Writes the attributes from every registered source to [sink]; called by the instrumentation that records screen transitions.
     */
    fun collectScreenAttributes(sink: (key: String, value: String) -> Unit)
}
