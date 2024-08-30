package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A [DeliveryService] that records the last parameters used to invoke each method, and for the ones that need it, count the number of
 * invocations. Please add additional tracking functionality as tests require them.
 */
public open class FakeDeliveryService : DeliveryService {
    public var lastSentCrash: EventMessage? = null
    public val lastSentLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    public val lastSavedLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    public val sentMoments: MutableList<EventMessage> = mutableListOf()
    public var lastEventSentAsync: EventMessage? = null
    public var eventSentAsyncInvokedCount: Int = 0
    public var lastSavedCrash: EventMessage? = null
    public var lastSentCachedSession: String? = null
    public val sentSessionEnvelopes: Queue<Pair<Envelope<SessionPayload>, SessionSnapshotType>> =
        ConcurrentLinkedQueue()
    public val savedSessionEnvelopes: Queue<Pair<Envelope<SessionPayload>, SessionSnapshotType>> =
        ConcurrentLinkedQueue()

    override fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
        if (snapshotType != SessionSnapshotType.PERIODIC_CACHE) {
            sentSessionEnvelopes.add(envelope to snapshotType)
        }
        savedSessionEnvelopes.add(envelope to snapshotType)
    }

    override fun sendCachedSessions(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        sessionIdTracker: SessionIdTracker
    ) {
        lastSentCachedSession = sessionIdTracker.getActiveSessionId()
    }

    override fun sendMoment(eventMessage: EventMessage) {
        eventSentAsyncInvokedCount++
        lastEventSentAsync = eventMessage
        sentMoments.add(eventMessage)
    }

    override fun sendLogs(logEnvelope: Envelope<LogPayload>) {
        lastSentLogPayloads.add(logEnvelope)
    }

    override fun saveLogs(logEnvelope: Envelope<LogPayload>) {
        lastSavedLogPayloads.add(logEnvelope)
    }

    public fun getSentSessions(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.FOREGROUND }.map { it.first }
    }

    public fun getSentBackgroundActivities(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.BACKGROUND }.map { it.first }
    }

    private fun Envelope<SessionPayload>.findAppState(): ApplicationState {
        val value = findSessionSpan().attributes?.findAttributeValue(embState.name)?.uppercase(Locale.ENGLISH)
        return ApplicationState.valueOf(checkNotNull(value))
    }

    public fun getLastSentSession(): Envelope<SessionPayload>? {
        return getSentSessions().lastOrNull()
    }

    public fun getLastSentBackgroundActivity(): Envelope<SessionPayload>? {
        return getSentBackgroundActivities().lastOrNull()
    }

    public fun getLastSavedSession(): Envelope<SessionPayload>? {
        return savedSessionEnvelopes.map { it.first }.lastOrNull {
            it.findAppState() == ApplicationState.FOREGROUND
        }
    }

    public fun getLastSavedBackgroundActivity(): Envelope<SessionPayload>? {
        return savedSessionEnvelopes.map { it.first }.lastOrNull {
            it.findAppState() == ApplicationState.BACKGROUND
        }
    }
}
