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
open class FakeDeliveryService : DeliveryService {
    val lastSentLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    val lastSavedLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    val sentMoments: MutableList<EventMessage> = mutableListOf()
    var lastEventSentAsync: EventMessage? = null
    var eventSentAsyncInvokedCount: Int = 0
    var lastSentCachedSession: String? = null
    val sentSessionEnvelopes: Queue<Pair<Envelope<SessionPayload>, SessionSnapshotType>> =
        ConcurrentLinkedQueue()
    val savedSessionEnvelopes: Queue<Pair<Envelope<SessionPayload>, SessionSnapshotType>> =
        ConcurrentLinkedQueue()

    override fun sendSession(envelope: Envelope<SessionPayload>, snapshotType: SessionSnapshotType) {
        savedSessionEnvelopes.add(envelope to snapshotType)
        if (snapshotType != SessionSnapshotType.PERIODIC_CACHE) {
            sentSessionEnvelopes.add(envelope to snapshotType)
        }
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

    fun getSentSessions(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.FOREGROUND }.map { it.first }
    }

    fun getSentBackgroundActivities(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.BACKGROUND }.map { it.first }
    }

    fun getSavedBackgroundActivities(): List<Envelope<SessionPayload>> {
        return savedSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.BACKGROUND }.map { it.first }
    }

    private fun Envelope<SessionPayload>.findAppState(): ApplicationState {
        val value = findSessionSpan().attributes?.findAttributeValue(embState.name)?.uppercase(Locale.ENGLISH)
        return ApplicationState.valueOf(checkNotNull(value))
    }

    fun getLastSentSession(): Envelope<SessionPayload>? {
        return getSentSessions().lastOrNull()
    }

    fun getLastSavedSession(): Envelope<SessionPayload>? {
        return savedSessionEnvelopes.map { it.first }.lastOrNull {
            it.findAppState() == ApplicationState.FOREGROUND
        }
    }
}
