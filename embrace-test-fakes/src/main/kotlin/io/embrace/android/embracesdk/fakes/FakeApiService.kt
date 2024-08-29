package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.CachedConfig
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
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

public class FakeApiService : ApiService {

    public var throwExceptionSendSession: Boolean = false
    private val serializer = EmbraceSerializer()
    public val sentLogPayloads: MutableList<LogPayload> = mutableListOf()
    public val savedLogPayloads: MutableList<LogPayload> = mutableListOf()
    public val eventRequests: MutableList<EventMessage> = mutableListOf()
    public val crashRequests: MutableList<EventMessage> = mutableListOf()
    public val sessionRequests: MutableList<Envelope<SessionPayload>> = mutableListOf()
    public var futureGetCount: Int = 0

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

    override fun sendEvent(eventMessage: EventMessage) {
        eventRequests.add(eventMessage)
    }

    override fun sendCrash(crash: EventMessage): Future<*> {
        crashRequests.add(crash)
        return ObservableFutureTask { }
    }

    override fun sendSession(action: SerializationAction, onFinish: ((successful: Boolean) -> Unit)?): Future<*> {
        if (throwExceptionSendSession) {
            error("FakeApiService.sendSession")
        }
        val stream = ByteArrayOutputStream()
        action(stream)
        val obj = readBodyAsSessionEnvelope(stream.toByteArray().inputStream())
        sessionRequests.add(obj)
        onFinish?.invoke(true)
        return ObservableFutureTask { }
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
    }

    private fun readBodyAsSessionEnvelope(inputStream: InputStream): Envelope<SessionPayload> {
        return GZIPInputStream(inputStream).use {
            serializer.fromJson(it, Envelope.sessionEnvelopeType)
        }
    }

    public inner class ObservableFutureTask<T>(callable: Callable<T>) : FutureTask<T>(callable) {
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
