package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.CachedConfig
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService

internal class FakeApiService : ApiService {

    private lateinit var deliveryCacheManager: DeliveryCacheManager
    private lateinit var scheduledExecutorService: ScheduledExecutorService

    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }

    override fun initForDelivery(
        cacheManager: DeliveryCacheManager,
        scheduledExecutorService: ScheduledExecutorService,
        networkConnectivityService: NetworkConnectivityService,
    ) {
        this.deliveryCacheManager = cacheManager
        this.scheduledExecutorService = scheduledExecutorService
    }

    override fun sendLogs(eventMessage: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        TODO("Not yet implemented")
    }

    override fun sendEvent(eventMessage: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendEventAndWait(eventMessage: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendCrash(crash: EventMessage) {
        TODO("Not yet implemented")
    }

    override fun sendAEIBlob(appExitInfoData: List<AppExitInfoData>) {
        TODO("Not yet implemented")
    }

    override fun sendSession(backgroundActivity: ByteArray, onFinish: (() -> Unit)?): Future<*> {
        TODO("Not yet implemented")
    }
}
