package io.embrace.android.embracesdk.internal.event

import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.Event
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.worker.ScheduledWorker
import java.util.concurrent.TimeUnit

/**
 * The time default period after which an event is considered 'late'.
 */
private const val DEFAULT_LATE_THRESHOLD_MILLIS = 5000L

/**
 * This class is in charge of building events and sending them to our servers.
 */
internal class EventHandler(
    private val metadataService: MetadataService,
    private val sessionIdTracker: SessionIdTracker,
    private val processStateService: ProcessStateService,
    private val configService: ConfigService,
    private val userService: UserService,
    private val deliveryService: DeliveryService,
    private val logger: EmbLogger,
    private val clock: Clock,
    private val scheduledWorker: ScheduledWorker
) {
    /**
     * Responsible for handling the start of an event.
     */
    fun onEventStarted(
        eventId: String,
        eventName: String,
        startTime: Long,
        sessionPropertiesService: SessionPropertiesService,
        eventProperties: Map<String, Any>?,
        timeoutCallback: Runnable
    ): EventDescription {
        val threshold = calculateLateThreshold(eventId)
        val event = buildStartEvent(
            eventId,
            eventName,
            startTime,
            threshold,
            sessionPropertiesService,
            eventProperties
        )

        val timer = scheduledWorker.schedule<Unit>(
            timeoutCallback,
            threshold - calculateOffset(startTime, threshold),
            TimeUnit.MILLISECONDS
        )

        if (shouldSendMoment(eventName)) {
            val eventMessage = buildStartEventMessage(event)
            deliveryService.sendMoment(eventMessage)
        } else {
            logger.logDebug("$eventName start moment not sent based on gating config.")
        }

        return EventDescription(timer, event)
    }

    /**
     * Responsible for handling ending an event.
     *
     * @return the event message for the end event
     */
    fun onEventEnded(
        originEventDescription: EventDescription,
        late: Boolean,
        eventProperties: Map<String, Any>?,
        sessionPropertiesService: SessionPropertiesService
    ): EventMessage {
        val event: Event = originEventDescription.event
        val startTime = event.timestamp ?: 0
        val endTime = clock.now()
        val duration = Math.max(0, endTime - startTime)
        // cancel late scheduler
        originEventDescription.lateTimer.cancel(false)

        val endEvent = buildEndEvent(
            event,
            endTime,
            duration,
            late,
            sessionPropertiesService,
            eventProperties
        )
        val endEventMessage = buildEndEventMessage(endEvent)

        if (shouldSendMoment(event.name)) {
            deliveryService.sendMoment(endEventMessage)
        } else {
            logger.logDebug("${event.name} end moment not sent based on gating config.")
        }

        return endEventMessage
    }

    /**
     * It determines if given event is allowed to be started.
     */
    fun isAllowedToStart(eventName: String): Boolean {
        return if (eventName.isNullOrEmpty()) {
            logger.logWarning("Event name is empty. Ignoring this event.")
            false
        } else if (!configService.dataCaptureEventBehavior.isEventEnabled(eventName)) {
            logger.logWarning("Event disabled. Ignoring event with name $eventName")
            false
        } else {
            true
        }
    }

    fun buildStartupEventInfo(originEvent: Event, endEvent: Event): StartupEventInfo =
        StartupEventInfo(
            endEvent.duration,
            originEvent.lateThreshold
        )

    private fun buildEndEventMessage(event: Event) =
        EventMessage(
            event = event,
            userInfo = userService.getUserInfo()
        )

    /**
     * Checks if the moment (startup moment or a regular moment) should not be sent based on the
     * gating config.
     *
     * @param name of the moment
     * @return true if should be gated
     */
    private fun shouldSendMoment(name: String?): Boolean {
        return if (name == STARTUP_EVENT_NAME) {
            !configService.sessionBehavior.shouldGateStartupMoment()
        } else {
            !configService.sessionBehavior.shouldGateMoment()
        }
    }

    private fun buildStartEventMessage(event: Event) =
        EventMessage(
            event = event,
            userInfo = userService.getUserInfo(),
            appInfo = metadataService.getAppInfo(),
            deviceInfo = metadataService.getDeviceInfo()
        )

    private fun buildStartEvent(
        eventId: String,
        eventName: String,
        startTime: Long,
        threshold: Long,
        sessionPropertiesService: SessionPropertiesService,
        eventProperties: Map<String, Any>?
    ): Event {
        return Event(
            name = eventName,
            sessionId = sessionIdTracker.getActiveSessionId(),
            eventId = eventId,
            type = EventType.START,
            appState = processStateService.getAppState(),
            lateThreshold = threshold,
            timestamp = startTime,
            sessionProperties = sessionPropertiesService.getProperties(),
            customProperties = eventProperties?.toMap()
        )
    }

    private fun buildEndEvent(
        originEvent: Event,
        endTime: Long,
        duration: Long,
        late: Boolean,
        sessionPropertiesService: SessionPropertiesService,
        eventProperties: Map<String, Any>?
    ): Event {
        return Event(
            name = originEvent.name,
            eventId = originEvent.eventId,
            sessionId = sessionIdTracker.getActiveSessionId(),
            timestamp = endTime,
            duration = duration,
            appState = processStateService.getAppState(),
            type = if (late) EventType.LATE else EventType.END,
            customProperties = eventProperties?.toMap(),
            sessionProperties = sessionPropertiesService.getProperties()
        )
    }

    private fun calculateOffset(startTime: Long, threshold: Long): Long {
        // Ensure we adjust the threshold to take into account backdated events
        return Math.min(threshold, Math.max(0, clock.now() - startTime))
    }

    private fun calculateLateThreshold(eventId: String): Long {
        // Check whether a late threshold has been configured, otherwise use the default
        val limits = configService.dataCaptureEventBehavior.getEventLimits()

        val value = limits[eventId]

        return when {
            value == null || !limits.containsKey(eventId) -> DEFAULT_LATE_THRESHOLD_MILLIS
            else -> value
        }
    }
}
