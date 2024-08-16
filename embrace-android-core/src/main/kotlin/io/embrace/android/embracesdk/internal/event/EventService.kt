package io.embrace.android.embracesdk.internal.event

import java.io.Closeable

public const val STARTUP_EVENT_NAME: String = "_startup"

/**
 * Provides event lifecycle management for the SDK.
 *
 * A start event is submitted, followed by an end event, and then the duration is timed. These
 * events appear on the session timeline.
 *
 * A story ID refers to the UUID for a particular event. An event ID is the concatenation of the
 * user-supplied event name, and the event identifier.
 */
public interface EventService : Closeable {

    /**
     * Starts an event.
     *
     * @param name the name of the event
     */
    public fun startEvent(name: String)

    /**
     * Starts an event.
     *
     * @param name       the name of the event
     * @param identifier the identifier, to uniquely distinguish between events with the same name
     */
    public fun startEvent(name: String, identifier: String?)

    /**
     * Starts an event.
     *
     * @param name       the name of the event
     * @param identifier the identifier, to uniquely distinguish between events with the same name
     * @param properties custom properties which can be sent as part of the request
     */
    public fun startEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?
    )

    /**
     * Starts an event.
     *
     * @param name            the name of the event
     * @param identifier      the identifier, to uniquely distinguish between events with the same name
     * @param properties      custom properties which can be sent as part of the request
     * @param startTime       a back-dated time at which the event occurred
     */
    public fun startEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?,
        startTime: Long?
    )

    /**
     * Ends an event which matches the given name.
     *
     * @param name the name of the event to terminate
     */
    public fun endEvent(name: String)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param identifier the unique identifier of the event to terminate
     */
    public fun endEvent(name: String, identifier: String?)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param properties custom properties which can be sent as part of the moment
     */
    public fun endEvent(name: String, properties: Map<String, Any>?)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param identifier the unique identifier of the event to terminate
     * @param properties custom properties which can be sent as part of the moment
     */
    public fun endEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?
    )

    /**
     * Finds all event IDs (event UUIDs) within the given time window.
     *
     * @return the list of story IDs within the specified range
     */
    public fun findEventIdsForSession(): List<String>

    /**
     * Gets all of the IDs of the currently active moments.
     *
     * @return list of IDs for the currently active moments
     */
    public fun getActiveEventIds(): List<String>?

    /**
     * Get startup duration and startup threshold info of the cold start session.
     *
     * @return the startup moment info
     */
    public fun getStartupMomentInfo(): StartupEventInfo?

    /**
     * Triggered when the application startup has started;
     */
    public fun sendStartupMoment()

    /**
     * Set a flag if the process was started by a data notification;
     * Used to avoid track startup times since arrive a notification;
     */
    public fun setProcessStartedByNotification()
}
