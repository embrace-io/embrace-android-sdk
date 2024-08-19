package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.config.RemoteConfigSource
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import java.util.concurrent.Future

public interface ApiService : RemoteConfigSource, NetworkConnectivityListener {

    /**
     * Sends a list of OTel Logs to the API.
     *
     * @param logEnvelope containing the logs
     */
    public fun sendLogEnvelope(logEnvelope: Envelope<LogPayload>)

    /**
     * Saves a list of OTel Logs to disk to be sent on restart.
     *
     * @param logEnvelope containing the logs
     */
    public fun saveLogEnvelope(logEnvelope: Envelope<LogPayload>)

    /**
     * Sends an event to the API.
     *
     * @param eventMessage the event message containing the event
     */
    public fun sendEvent(eventMessage: EventMessage)

    /**
     * Sends a crash event to the API and reschedules it if the request times out
     *
     * @param crash the event message containing the crash
     */
    public fun sendCrash(crash: EventMessage): Future<*>

    /**
     * Sends a session to the API. This can be either a v1 or v2 session - the implementation
     * is responsible for routing the payload correctly.
     */
    public fun sendSession(action: SerializationAction, onFinish: ((successful: Boolean) -> Unit)?): Future<*>?
}
