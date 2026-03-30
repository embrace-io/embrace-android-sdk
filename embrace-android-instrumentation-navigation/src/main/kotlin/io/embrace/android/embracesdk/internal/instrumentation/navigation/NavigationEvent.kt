package io.embrace.android.embracesdk.internal.instrumentation.navigation

/**
 * Typed events representing navigation-related signals from various sources
 */
internal sealed class NavigationEvent(
    val name: String
) {
    /**
     * An Activity is about to be started.
     */
    data class ActivityStarted(private var activityName: String) : NavigationEvent(name = activityName)

    /**
     * The app has backgrounded, i.e. it has no visible activities
     */
    object Backgrounded : NavigationEvent("Backgrounded")
}
