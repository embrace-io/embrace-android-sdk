package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logs.LogPayload
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
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
     * @param logsEnvelope containing the logs
     */
    fun sendLogsEnvelope(logsEnvelope: Envelope<LogPayload>)

    /**
     * Sends a session to the API.
     *
     * @param sessionEnvelope containing the session
     */
    fun sendSessionEnvelope(sessionEnvelope: Envelope<SessionPayload>)

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
    fun sendSession(action: SerializationAction, onFinish: (() -> Unit)?): Future<*>?
}
