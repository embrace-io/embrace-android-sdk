package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.getId

/**
 * Typed events representing navigation-related signals from various sources.
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

    /**
     * The times at which the event was detected and generated. Any downstream processing ascribing a time to this event should
     * use this value rather than the time the processing is taking place
     */
    val timestampMs: Long,
) {
    /**
     * An Activity is about to be started.
     */
    class ActivityStarted(
        activity: Activity,
        timestampMs: Long,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId(),
        timestampMs = timestampMs,
    )

    /**
     * An Activity is about to resume, i.e. become visible
     */
    class ActivityResumed(
        activity: Activity,
        timestampMs: Long,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId(),
        timestampMs = timestampMs,
    )

    /**
     * An Activity has paused
     */
    class ActivityPaused(
        activity: Activity,
        timestampMs: Long,
    ) : NavigationEvent(
        name = activity.localClassName,
        componentId = activity.getId(),
        timestampMs = timestampMs,
    )

    /**
     * A NavController was found and a listener was attached for the given [Activity] instance.
     */
    class NavControllerAttached(
        activity: Activity,
        timestampMs: Long,
    ) : NavigationEvent(
        componentId = activity.getId(),
        timestampMs = timestampMs,
    )

    /**
     * A NavController reported a new destination for the given [Activity] instance.
     */
    class NavControllerDestinationChanged(
        activity: Activity,
        screenName: String,
        timestampMs: Long,
    ) : NavigationEvent(
        name = screenName,
        componentId = activity.getId(),
        timestampMs = timestampMs,
    )

    /**
     * The app has backgrounded, i.e. it has no visible activities.
     */
    class Backgrounded(
        timestampMs: Long,
    ) : NavigationEvent(
        name = "Backgrounded",
        timestampMs = timestampMs,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NavigationEvent

        if (componentId != other.componentId) return false
        if (timestampMs != other.timestampMs) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = componentId
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
