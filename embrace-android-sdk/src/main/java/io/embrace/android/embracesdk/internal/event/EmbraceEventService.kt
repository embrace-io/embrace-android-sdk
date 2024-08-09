package io.embrace.android.embracesdk.internal.event

import io.embrace.android.embracesdk.internal.EventDescription
import io.embrace.android.embracesdk.internal.StartupEventInfo
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.lifecycle.StartupListener
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.utils.stream
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.WorkerName
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
    processStateService: ProcessStateService,
    sessionIdTracker: SessionIdTracker,
    userService: UserService,
    private val sessionPropertiesService: SessionPropertiesService,
    private val logger: EmbLogger,
    workerThreadModule: WorkerThreadModule,
    private val clock: Clock
) : EventService, ProcessStateListener, MemoryCleanerListener, StartupListener {
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
            processStateService,
            configService,
            userService,
            deliveryService,
            logger,
            clock,
            workerThreadModule.scheduledWorker(WorkerName.BACKGROUND_REGISTRATION)
        )
        backgroundWorker =
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
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
        } else if (configService.startupBehavior.isAutomaticEndEnabled()) {
            endEvent(STARTUP_EVENT_NAME)
        }
    }

    override fun sendStartupMoment() {
        synchronized(this) {
            if (startupSent) {
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
            if (!eventHandler.isAllowedToStart(name)) {
                return
            }
            val eventKey = getInternalEventKey(name, identifier)
            if (activeEvents.containsKey(eventKey)) {
                endEvent(name, identifier, false, null)
            }
            val now = clock.now()
            if (sanitizedStartTime == null) {
                sanitizedStartTime = now
            }
            val eventId = Uuid.getEmbUuid()
            eventIds.add(eventId)
            val eventDescription = eventHandler.onEventStarted(
                eventId,
                name,
                sanitizedStartTime,
                sessionPropertiesService,
                properties,
                Runnable { endEvent(name, identifier, true, null) }
            )

            // event started, update active events
            activeEvents[eventKey] = eventDescription
        } catch (ex: Exception) {
            logger.logError(
                "Cannot start event with name: $name, identifier: $identifier due to an exception",
                ex
            )
            logger.trackInternalError(InternalErrorType.START_EVENT_FAIL, ex)
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
                sessionPropertiesService
            )
            if (isStartupEvent(name)) {
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
            logger.trackInternalError(InternalErrorType.END_EVENT_FAIL, ex)
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

    companion object {
        const val STARTUP_EVENT_NAME = "_startup"

        internal fun getInternalEventKey(eventName: String, identifier: String?): String =
            when (identifier) {
                null, "" -> eventName
                else -> "$eventName#$identifier"
            }

        internal fun isStartupEvent(eventName: String): Boolean = STARTUP_EVENT_NAME == eventName
    }
}
