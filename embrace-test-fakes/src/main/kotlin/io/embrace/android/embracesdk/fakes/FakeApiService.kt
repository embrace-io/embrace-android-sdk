package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class FakeApiService : ApiService {

    var throwExceptionSendSession: Boolean = false
    private val serializer = EmbraceSerializer()
    val sentLogPayloads: MutableList<LogPayload> = mutableListOf()
    val savedLogPayloads: MutableList<LogPayload> = mutableListOf()
    val sessionRequests: MutableList<Envelope<SessionPayload>> = mutableListOf()
    var futureGetCount: Int = 0

    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }

    override fun sendLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        sentLogPayloads.add(logEnvelope.data)
    }

    override fun saveLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        savedLogPayloads.add(logEnvelope.data)
    }

    override fun sendSession(action: SerializationAction, onFinish: ((response: ApiResponse) -> Unit)): Future<*> {
        if (throwExceptionSendSession) {
            error("FakeApiService.sendSession")
        }
        val stream = ByteArrayOutputStream()
        action(stream)
        val obj = readBodyAsSessionEnvelope(stream.toByteArray().inputStream())
        sessionRequests.add(obj)
        onFinish(ApiResponse.None)
        return ObservableFutureTask { }
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
    }

    private fun readBodyAsSessionEnvelope(inputStream: InputStream): Envelope<SessionPayload> {
        return GZIPInputStream(inputStream).use {
            serializer.fromJson(it, Envelope.sessionEnvelopeType)
        }
    }

    inner class ObservableFutureTask<T>(callable: Callable<T>) : FutureTask<T>(callable) {
        override fun get(): T {
            futureGetCount++
            return super.get()
        }

        override fun get(timeout: Long, unit: TimeUnit): T {
            futureGetCount++
            return super.get(timeout, unit)
        }
    }
}
