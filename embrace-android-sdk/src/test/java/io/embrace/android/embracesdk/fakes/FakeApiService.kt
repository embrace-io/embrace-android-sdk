package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.CachedConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logs.LogPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.BlobMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkEvent
import io.embrace.android.embracesdk.payload.SessionMessage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.zip.GZIPInputStream

internal class FakeApiService : ApiService {

    var throwExceptionSendSession: Boolean = false
    private val serializer = EmbraceSerializer()
    val logRequests = mutableListOf<EventMessage>()
    val logPayloads = mutableListOf<LogPayload>()
    val networkCallRequests = mutableListOf<NetworkEvent>()
    val eventRequests = mutableListOf<EventMessage>()
    val crashRequests = mutableListOf<EventMessage>()
    val blobRequests = mutableListOf<BlobMessage>()
    val sessionRequests = mutableListOf<SessionMessage>()
    val bgActivityRequests = mutableListOf<SessionMessage>()

    override fun getConfig(): RemoteConfig? {
        TODO("Not yet implemented")
    }

    override fun getCachedConfig(): CachedConfig {
        TODO("Not yet implemented")
    }

    override fun sendLog(eventMessage: EventMessage) {
        logRequests.add(eventMessage)
    }

    override fun sendLogs(logPayload: LogPayload) {
        logPayloads.add(logPayload)
    }

    override fun sendNetworkCall(networkEvent: NetworkEvent) {
        networkCallRequests.add(networkEvent)
    }

    override fun sendEvent(eventMessage: EventMessage) {
        eventRequests.add(eventMessage)
    }

    override fun sendCrash(crash: EventMessage): Future<*> {
        crashRequests.add(crash)
        return FutureTask { }
    }

    override fun sendAEIBlob(blobMessage: BlobMessage) {
        blobRequests.add(blobMessage)
    }

    override fun sendSession(action: SerializationAction, onFinish: (() -> Unit)?): Future<*>? {
        if (throwExceptionSendSession) {
            error("FakeApiService.sendSession")
        }
        val stream = ByteArrayOutputStream()
        action(stream)
        val obj = readBodyAsSessionMessage(stream.toByteArray().inputStream())
        sessionRequests.add(obj)
        return FutureTask { }
    }

    private fun readBodyAsSessionMessage(inputStream: InputStream): SessionMessage {
        return GZIPInputStream(inputStream).use {
            serializer.fromJson(it, SessionMessage::class.java)
        }
    }
}
