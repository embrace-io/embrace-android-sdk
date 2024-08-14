package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.event.EventService

/**
 * Holds dependencies that normally act as a 'container' for other data. For example,
 * a span, an Event, PerformanceInfo, etc.
 */
internal interface MomentsModule {
    val eventService: EventService
}
