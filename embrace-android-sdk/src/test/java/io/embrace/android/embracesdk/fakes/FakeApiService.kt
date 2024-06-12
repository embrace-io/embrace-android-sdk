package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.CachedConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

internal class FakeApiService : ApiService {

    var throwExceptionSendSession: Boolean = false
    private val serializer = EmbraceSerializer()
    val logRequests = mutableListOf<EventMessage>()
    val sentLogPayloads = mutableListOf<LogPayload>()
    val savedLogPayloads = mutableListOf<LogPayload>()
    val networkCallRequests = mutableListOf<NetworkEvent>()
    val eventRequests = mutableListOf<EventMessage>()
    val crashRequests = mutableListOf<EventMessage>()
    val sessionRequests = mutableListOf<SessionMessage>()
    var futureGetCount: Int = 0

    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }

    override fun sendLog(eventMessage: EventMessage) {
        logRequests.add(eventMessage)
    }

    override fun sendLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        sentLogPayloads.add(logEnvelope.data)
    }

    override fun saveLogEnvelope(logEnvelope: Envelope<LogPayload>) {
        savedLogPayloads.add(logEnvelope.data)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        networkCallRequests.add(networkEvent)
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
        val obj = readBodyAsSessionMessage(stream.toByteArray().inputStream())
        sessionRequests.add(obj)
        onFinish?.invoke(true)
        return ObservableFutureTask { }
    }

    private fun readBodyAsSessionMessage(inputStream: InputStream): SessionMessage {
        return GZIPInputStream(inputStream).use {
            serializer.fromJson(it, SessionMessage::class.java)
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
