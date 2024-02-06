package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.util.concurrent.Future

internal interface ApiService {
    fun getConfig(): RemoteConfig?
    fun getCachedConfig(): CachedConfig
    fun sendLog(eventMessage: EventMessage)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendEvent(eventMessage: EventMessage)
    fun sendCrash(crash: EventMessage): Future<*>
    fun sendAEIBlob(blobMessage: BlobMessage)
    fun sendSession(action: SerializationAction, onFinish: (() -> Unit)?): Future<*>?
}
