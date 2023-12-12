package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.CachedConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.util.concurrent.Future

internal class FakeApiService : ApiService {

    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }

    override fun sendLog(eventMessage: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        TODO("Not yet implemented")
    }

    override fun sendEvent(eventMessage: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendCrash(crash: EventMessage): Future<*> {
        TODO("Not yet implemented")
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        TODO("Not yet implemented")
    }

    override fun sendSession(sessionPayload: ByteArray, onFinish: (() -> Unit)?): Future<*> {
        TODO("Not yet implemented")
    }
}
