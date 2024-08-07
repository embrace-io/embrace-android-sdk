package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.config.RemoteConfigSource
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.NetworkEvent
import java.util.concurrent.Future

internal interface ApiService : RemoteConfigSource {

    /**
     * Sends a log message to the API.
     *
     * @param eventMessage the event message containing the log entry
     * @return a future containing the response body from the server
     */
    fun sendLog(eventMessage: EventMessage)

    /**
     * Sends a list of OTel Logs to the API.
     *
     * @param logEnvelope containing the logs
     */
    fun sendLogEnvelope(logEnvelope: Envelope<LogPayload>)

    /**
     * Saves a list of OTel Logs to disk to be sent on restart.
     *
     * @param logEnvelope containing the logs
     */
    fun saveLogEnvelope(logEnvelope: Envelope<LogPayload>)

    /**
     * Sends a network event to the API.
     *
     * @param networkEvent the event containing the network call information
     */
    fun sendNetworkCall(networkEvent: NetworkEvent)

    /**
     * Sends an event to the API.
     *
     * @param eventMessage the event message containing the event
     */
    fun sendEvent(eventMessage: EventMessage)

    /**
     * Sends a crash event to the API and reschedules it if the request times out
     *
     * @param crash the event message containing the crash
     */
    fun sendCrash(crash: EventMessage): Future<*>

    /**
     * Sends a session to the API. This can be either a v1 or v2 session - the implementation
     * is responsible for routing the payload correctly.
     */
    fun sendSession(action: SerializationAction, onFinish: ((successful: Boolean) -> Unit)?): Future<*>?
}
