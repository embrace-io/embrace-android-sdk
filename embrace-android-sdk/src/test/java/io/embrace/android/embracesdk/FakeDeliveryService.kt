package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent

/**
 * A [DeliveryService] that records the last parameters used to invoke each method, and for the ones that need it, count the number of
 * invocations. Please add additional tracking functionality as tests require them.
 */
internal open class FakeDeliveryService : DeliveryService {
    var lastSentNetworkCall: NetworkEvent? = null
    var lastSentCrash: EventMessage? = null
    val lastSentLogs: MutableList<EventMessage> = mutableListOf()
    val lastSentLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    val lastSavedLogPayloads: MutableList<Envelope<LogPayload>> = mutableListOf()
    val sentMoments: MutableList<EventMessage> = mutableListOf()
    var lastEventSentAsync: EventMessage? = null
    var eventSentAsyncInvokedCount: Int = 0
    var lastSavedCrash: EventMessage? = null
    var lastSentCachedSession: String? = null
    val sentSessionEnvelopes: MutableList<Pair<Envelope<SessionPayload>, SessionSnapshotType>> = mutableListOf()
    val savedSessionEnvelopes: MutableList<Pair<Envelope<SessionPayload>, SessionSnapshotType>> = mutableListOf()

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

    override fun sendLog(eventMessage: EventMessage) {
        lastSentLogs.add(eventMessage)
    }

    override fun sendLogs(logEnvelope: Envelope<LogPayload>) {
        lastSentLogPayloads.add(logEnvelope)
    }

    override fun saveLogs(logEnvelope: Envelope<LogPayload>) {
        lastSavedLogPayloads.add(logEnvelope)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        lastSentNetworkCall = networkEvent
    }

    override fun sendCrash(crash: EventMessage, processTerminating: Boolean) {
        lastSavedCrash = crash
        lastSentCrash = crash
    }

    fun getSentSessions(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.FOREGROUND }.map { it.first }
    }

    fun getSentBackgroundActivities(): List<Envelope<SessionPayload>> {
        return sentSessionEnvelopes.filter { it.first.findAppState() == ApplicationState.BACKGROUND }.map { it.first }
    }

    private fun Envelope<SessionPayload>.findAppState(): ApplicationState {
        val value = findSessionSpan().attributes?.findAttributeValue(embState.name)?.toUpperCase()
        return ApplicationState.valueOf(checkNotNull(value))
    }

    fun getLastSentSession(): Envelope<SessionPayload>? {
        return getSentSessions().lastOrNull()
    }

    fun getLastSentBackgroundActivity(): Envelope<SessionPayload>? {
        return getSentBackgroundActivities().lastOrNull()
    }

    fun getLastSavedSession(): Envelope<SessionPayload>? {
        return savedSessionEnvelopes.map { it.first }.lastOrNull {
            it.findAppState() == ApplicationState.FOREGROUND
        }
    }

    fun getLastSavedBackgroundActivity(): Envelope<SessionPayload>? {
        return savedSessionEnvelopes.map { it.first }.lastOrNull {
            it.findAppState() == ApplicationState.BACKGROUND
        }
    }
}
