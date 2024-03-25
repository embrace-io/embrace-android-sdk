package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.util.concurrent.Future

internal interface ApiService {

    /**
     * Asynchronously gets the app's SDK configuration.
     *
     * These settings define app-specific settings, such as disabled log patterns, whether
     * screenshots are enabled, as well as limits and thresholds.
     *
     * @return a future containing the configuration.
     */
    fun getConfig(): RemoteConfig?

    fun getCachedConfig(): CachedConfig

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
     * Sends an Application Exit Info (AEI) blob message to the API.
     *
     * @param blobMessage the blob message containing the AEI data
     * @return a future containing the response body from the server
     */
    fun sendAEIBlob(blobMessage: BlobMessage)

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
    fun sendSession(isV2: Boolean, action: SerializationAction, onFinish: ((successful: Boolean) -> Unit)?): Future<*>?
}
