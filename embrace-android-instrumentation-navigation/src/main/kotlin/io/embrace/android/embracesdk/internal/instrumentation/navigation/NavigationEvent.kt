package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity

/**
 * Typed events representing navigation-related signals from various sources
 */
internal sealed class NavigationEvent(
    /**
     * The name used to identify the event. For some events, it can be used as a value for [NavigationStateDataSource]
     */
    val name: String = "",

    /**
     * The unique ID of the component the fired the navigation event. Different instances of the same component should generate
     * different IDs, and it's up to the event to determine what it is based on the input at construction time.
     */
    val componentId: Int = 0,
) {
    /**
     * An Activity is about to be started.
     */
    class ActivityStarted(
        activity: Activity,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId()
    )

    /**
     * An Activity is about to resume, i.e. become visible
     */
    class ActivityResumed(
        activity: Activity,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId()
    )

    /**
     * An Activity has paused
     */
    class ActivityPaused(
        activity: Activity,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId()
    )

    /**
     * The app has backgrounded, i.e. it has no visible activities
     */
    object Backgrounded : NavigationEvent("Backgrounded")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NavigationEvent

        if (componentId != other.componentId) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = componentId
        result = 31 * result + name.hashCode()
        return result
    }
}

internal fun Activity.getId(): Int = System.identityHashCode(this)
