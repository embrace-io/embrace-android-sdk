package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService

internal interface ApiService {
    fun getConfig(): RemoteConfig?
    fun getCachedConfig(): CachedConfig
    fun initForDelivery(
        cacheManager: DeliveryCacheManager,
        scheduledExecutorService: ScheduledExecutorService,
        networkConnectivityService: NetworkConnectivityService
    )
    fun sendLogs(eventMessage: EventMessage)
    fun sendNetworkCall(networkEvent: NetworkEvent)
    fun sendEvent(eventMessage: EventMessage)
    fun sendEventAndWait(eventMessage: EventMessage)
    fun sendCrash(crash: EventMessage)
    fun sendAEIBlob(appExitInfoData: List<AppExitInfoData>)
    fun sendSession(backgroundActivity: ByteArray, onFinish: (() -> Unit)?): Future<*>
}
