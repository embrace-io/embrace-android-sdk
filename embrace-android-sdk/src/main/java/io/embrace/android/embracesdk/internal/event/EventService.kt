package io.embrace.android.embracesdk.internal.event

import io.embrace.android.embracesdk.internal.StartupEventInfo
import java.io.Closeable

/**
 * Provides event lifecycle management for the SDK.
 *
 * A start event is submitted, followed by an end event, and then the duration is timed. These
 * events appear on the session timeline.
 *
 * A story ID refers to the UUID for a particular event. An event ID is the concatenation of the
 * user-supplied event name, and the event identifier.
 */
internal interface EventService : Closeable {

    /**
     * Starts an event.
     *
     * @param name the name of the event
     */
    fun startEvent(name: String)

    /**
     * Starts an event.
     *
     * @param name       the name of the event
     * @param identifier the identifier, to uniquely distinguish between events with the same name
     */
    fun startEvent(name: String, identifier: String?)

    /**
     * Starts an event.
     *
     * @param name       the name of the event
     * @param identifier the identifier, to uniquely distinguish between events with the same name
     * @param properties custom properties which can be sent as part of the request
     */
    fun startEvent(
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
    fun startEvent(
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
    fun endEvent(name: String)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param identifier the unique identifier of the event to terminate
     */
    fun endEvent(name: String, identifier: String?)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param properties custom properties which can be sent as part of the moment
     */
    fun endEvent(name: String, properties: Map<String, Any>?)

    /**
     * Ends an event which matches the given name and identifier.
     *
     * @param name       the name of the event to terminate
     * @param identifier the unique identifier of the event to terminate
     * @param properties custom properties which can be sent as part of the moment
     */
    fun endEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?
    )

    /**
     * Finds all event IDs (event UUIDs) within the given time window.
     *
     * @return the list of story IDs within the specified range
     */
    fun findEventIdsForSession(): List<String>

    /**
     * Gets all of the IDs of the currently active moments.
     *
     * @return list of IDs for the currently active moments
     */
    fun getActiveEventIds(): List<String>?

    /**
     * Get startup duration and startup threshold info of the cold start session.
     *
     * @return the startup moment info
     */
    fun getStartupMomentInfo(): StartupEventInfo?

    /**
     * Triggered when the application startup has started;
     */
    fun sendStartupMoment()

    /**
     * Set a flag if the process was started by a data notification;
     * Used to avoid track startup times since arrive a notification;
     */
    fun setProcessStartedByNotification()
}
