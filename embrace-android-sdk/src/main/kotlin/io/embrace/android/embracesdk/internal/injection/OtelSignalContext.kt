package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.state.StateSnapshot
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.opentelemetry.kotlin.semconv.UserAttributes

/**
 * Supplies the session and user context that is stamped on OTel signals.
 *
 * The services that hold this context are constructed after the OTel SDK, so they are supplied as forward
 * references. Signals recorded before [markReady] carry no context, and a log emitted before it carries no state in
 * either form.
 *
 * @param statesStampedByLogPipeline whether the log pipeline stamps the values [stateSnapshotProvider] supplies itself, by
 * who reads the log, so that [getEventMetadata] leaves them out. Otherwise they are stamped as one string that holds all
 * of them, which every reader of the log sees.
 */
internal class OtelSignalContext(
    private val sessionIdsProvider: Provider<SessionIdsProvider>,
    private val userServiceProvider: Provider<UserService>,
    private val sessionPartTrackerProvider: Provider<SessionPartTracker>,
    private val processStateTrackerProvider: Provider<ProcessStateTracker>,
    private val experimentTrackingServiceProvider: Provider<ExperimentTrackingService>,
    private val userSessionPropertiesServiceProvider: Provider<UserSessionPropertiesService>,
    private val instrumentationRegistryProvider: Provider<InstrumentationRegistry>,
    private val stateSnapshotProvider: Provider<StateSnapshot?> = { null },
    private val statesStampedByLogPipeline: Boolean = false,
) {

    @Volatile
    private var ready: Boolean = false

    /**
     * Called once every service that supplies context has been constructed.
     */
    fun markReady() {
        ready = true
    }

    fun getSessionIdsProvider(): SessionIdsProvider? = if (ready) {
        sessionIdsProvider()
    } else {
        null
    }

    fun getUserId(): String? = if (ready) {
        userServiceProvider().getUserInfo().userId
    } else {
        null
    }

    /**
     * The value of every state to stamp on a log emitted now, for a log pipeline that stamps state itself. Null before
     * [markReady], as a log emitted then carries none of the session's context, and where nothing records state.
     */
    fun getLogStateSnapshot(): StateSnapshot? = if (ready) {
        stateSnapshotProvider()
    } else {
        null
    }

    /**
     * A snapshot of the SDK state that is stamped on OTel log records at the time they are emitted. Every value is text.
     * The values of the states are left out where [statesStampedByLogPipeline].
     */
    @OptIn(ExperimentalSemconv::class)
    fun getEventMetadata(): Map<String, Any> {
        if (!ready) {
            return emptyMap()
        }
        return mutableMapOf<String, Any>().apply {
            val sessionPart = sessionPartTrackerProvider().getActiveSessionPart()
            val sessionState = sessionPart?.processState ?: processStateTrackerProvider().getAppState()
            val sessionIds = sessionIdsProvider().getActiveSessionIds()

            put(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionIds.sessionPartId)
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, sessionIds.userSessionId)
            put(EmbSessionAttributes.EMB_STATE, sessionState.description)
            userServiceProvider().getUserInfo().userId?.let {
                put(UserAttributes.USER_ID, it)
            }
            experimentTrackingServiceProvider().getRecords()?.let {
                put(EmbCommonAttributes.EMB_EXPERIMENTS, it)
            }
            putAll(
                userSessionPropertiesServiceProvider()
                    .getProperties()
                    .mapKeys { property ->
                        property.key.toEmbraceAttributeName()
                    },
            )
            val states = stateSnapshotProvider()
            if (states == null) {
                instrumentationRegistryProvider().getCurrentStates().forEach {
                    put(it.key, it.value.toString())
                }
            } else if (!statesStampedByLogPipeline && !states.isEmpty()) {
                // one snapshot, so the values are all of one moment, and its place in the order of state changes
                put(StateSnapshot.ATTRIBUTE, states.encoded)
                put(StateSnapshot.SEQUENCE_ATTRIBUTE, states.sequence.toString())
            }
        }
    }
}
