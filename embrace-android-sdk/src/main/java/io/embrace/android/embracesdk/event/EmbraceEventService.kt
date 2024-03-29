package io.embrace.android.embracesdk.event

import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.EventDescription
import io.embrace.android.embracesdk.internal.StartupEventInfo
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.toEmbraceSpanName
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.session.MemoryCleanerListener
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.utils.stream
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap

/**
 * Handles the lifecycle of events (moments).
 *
 *
 * An event is started, timed, and then ended. If the event takes longer than a specified period of
 * time, then the event is considered late.
 */
internal class EmbraceEventService(
    private val startupStartTimeMs: Long,
    deliveryService: DeliveryService,
    private val configService: ConfigService,
    metadataService: MetadataService,
    sessionIdTracker: SessionIdTracker,
    performanceInfoService: PerformanceInfoService,
    userService: UserService,
    private val sessionProperties: EmbraceSessionProperties,
    private val logger: InternalEmbraceLogger,
    workerThreadModule: WorkerThreadModule,
    private val clock: Clock,
    private val spanService: SpanService
) : EventService, ActivityLifecycleListener, ProcessStateListener, MemoryCleanerListener {
    private val backgroundWorker: BackgroundWorker

    /**
     * Timeseries of event IDs, keyed on the start time of the event.
     */
    private val eventIds = ConcurrentLinkedQueue<String>()

    /**
     * Map of active events, keyed on their event ID (event name + identifier).
     */
    val activeEvents: ConcurrentMap<String, EventDescription> = ConcurrentHashMap()

    private var startupEventInfo: StartupEventInfo? = null
    private var startupSent = false
    private var processStartedByNotification = false
    var eventHandler: EventHandler

    init {

        // Session properties
        eventHandler = EventHandler(
            metadataService,
            sessionIdTracker,
            configService,
            userService,
            performanceInfoService,
            deliveryService,
            logger,
            clock,
            workerThreadModule.scheduledWorker(WorkerName.BACKGROUND_REGISTRATION)
        )
        backgroundWorker =
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        logDeveloper("EmbraceEventService", "coldStart: $coldStart")
        if (coldStart) {
            // Using the system current timestamp here as the startup timestamp is related to the
            // the actual SDK starts ( when the app context starts ). The app context can start
            // in the background, registering a startup time that will later be sent with the
            // app coming to foreground, resulting in a *pretty* long startup moment.
            sendStartupMoment()
        }
    }

    override fun applicationStartupComplete() {
        if (processStartedByNotification) {
            activeEvents.remove(STARTUP_EVENT_NAME)
            logDeveloper("EmbraceEventService", "Application startup started by data notification")
        } else if (configService.startupBehavior.isAutomaticEndEnabled()) {
            logDeveloper("EmbraceEventService", "Automatically ending startup event")
            endEvent(STARTUP_EVENT_NAME)
        } else {
            logDeveloper("EmbraceEventService", "Application startup automatically end is disabled")
        }
    }

    override fun sendStartupMoment() {
        logDeveloper("EmbraceEventService", "sendStartupMoment")
        synchronized(this) {
            if (startupSent) {
                logDeveloper("EmbraceEventService", "Startup is already sent")
                return
            }
            startupSent = true
        }
        logger.logDebug("Sending startup start event.")
        startEvent(
            STARTUP_EVENT_NAME,
            null,
            null,
            startupStartTimeMs
        )
    }

    override fun setProcessStartedByNotification() {
        processStartedByNotification = true
    }

    override fun startEvent(name: String) {
        // extract constant
        startEvent(name, null, null, null)
    }

    override fun startEvent(name: String, identifier: String?) {
        startEvent(name, identifier, null, null)
    }

    override fun startEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        startEvent(name, identifier, properties, null)
    }

    override fun startEvent(
        name: String,
        identifier: String?,
        properties: Map<String, Any>?,
        startTime: Long?
    ) {
        var sanitizedStartTime = startTime
        try {
            logDeveloper("EmbraceEventService", "Start event: $name")
            if (!eventHandler.isAllowedToStart(name)) {
                logDeveloper("EmbraceEventService", "Event handler not allowed to start ")
                return
            }
            val eventKey = getInternalEventKey(name, identifier)
            if (activeEvents.containsKey(eventKey)) {
                logDeveloper("EmbraceEventService", "Ending previous event with same name")
                endEvent(name, identifier, false, null)
            }
            val now = clock.now()
            if (sanitizedStartTime == null) {
                sanitizedStartTime = now
            }
            val eventId = getEmbUuid()
            eventIds.add(eventId)
            val eventDescription = eventHandler.onEventStarted(
                eventId,
                name,
                sanitizedStartTime,
                sessionProperties,
                properties,
                Runnable { endEvent(name, identifier, true, null) }
            )

            // event started, update active events
            activeEvents[eventKey] = eventDescription
            logDeveloper("EmbraceEventService", "Event started : $name")
        } catch (ex: Exception) {
            logger.logError(
                "Cannot start event with name: $name, identifier: $identifier due to an exception",
                ex,
                false
            )
        }
    }

    override fun endEvent(name: String) {
        endEvent(name, null, false, null)
    }

    override fun endEvent(name: String, identifier: String?) {
        endEvent(name, identifier, false, null)
    }

    override fun endEvent(name: String, properties: Map<String, Any>?) {
        endEvent(name, null, false, properties)
    }

    override fun endEvent(name: String, identifier: String?, properties: Map<String, Any>?) {
        endEvent(name, identifier, false, properties)
    }

    private fun endEvent(
        name: String,
        identifier: String?,
        late: Boolean,
        properties: Map<String, Any>?
    ) {
        try {
            logDeveloper("EmbraceEventService", "Ending event: $name")
            val eventKey = getInternalEventKey(name, identifier)
            val originEventDescription: EventDescription? = when {
                late -> activeEvents[eventKey]
                else -> activeEvents.remove(eventKey)
            }
            if (originEventDescription == null) {
                // We avoid logging that there's no startup event in the activeEvents collection
                // as the user might have completed it manually on a @StartupActivity.
                if (!isStartupEvent(name)) {
                    logger.logError(
                        "No start event found when ending an event with name: $name, identifier: $identifier"
                    )
                }
                return
            }
            val (event) = eventHandler.onEventEnded(
                originEventDescription,
                late,
                properties,
                sessionProperties
            )
            if (isStartupEvent(name)) {
                if (!late) {
                    logStartupSpan()
                }
                logDeveloper("EmbraceEventService", "Ending Startup Ending")
                startupEventInfo = eventHandler.buildStartupEventInfo(
                    originEventDescription.event,
                    event
                )
            }
        } catch (ex: Exception) {
            logger.logError(
                "Cannot end event with name: $name, identifier: $identifier due to an exception",
                ex
            )
        }
    }

    override fun findEventIdsForSession(): List<String> =
        eventIds.toList()

    override fun getActiveEventIds(): List<String> {
        val ids: MutableList<String> = ArrayList()
        stream<EventDescription>(activeEvents.values) { (_, event): EventDescription ->
            ids.add(event.eventId)
        }
        return ids
    }

    override fun getStartupMomentInfo(): StartupEventInfo? = startupEventInfo

    override fun close() {
        cleanCollections()
    }

    override fun cleanCollections() {
        val activeEventIds = activeEvents.values.map { it.event.eventId }
        eventIds.removeAll {
            !activeEventIds.contains(it)
        }
    }

    /**
     * Return the active event with the given name and identifier if it exists. Return null otherwise.
     */
    fun getActiveEvent(eventName: String, identifier: String?): EventDescription? {
        return activeEvents[getInternalEventKey(eventName, identifier)]
    }

    private fun logStartupSpan() {
        val startupEndTimeMillis = clock.now()
        backgroundWorker.submit {
            spanService.recordCompletedSpan(
                name = STARTUP_SPAN_NAME,
                startTimeMs = startupStartTimeMs,
                endTimeMs = startupEndTimeMillis,
                internal = false
            )
        }
    }

    companion object {
        const val STARTUP_EVENT_NAME = "_startup"
        private val STARTUP_SPAN_NAME = "startup-moment".toEmbraceSpanName()

        internal fun getInternalEventKey(eventName: String, identifier: String?): String =
            when (identifier) {
                null, "" -> eventName
                else -> "$eventName#$identifier"
            }

        internal fun isStartupEvent(eventName: String): Boolean = STARTUP_EVENT_NAME == eventName
    }
}
