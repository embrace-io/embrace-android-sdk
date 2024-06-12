package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import java.lang.reflect.Type

internal class FakeCacheService : CacheService {
    override fun <T> cacheObject(name: String, objectToCache: T, type: Type) {
        TODO("Not yet implemented")
    }

    override fun <T> loadObject(name: String, type: Type): T? {
        TODO("Not yet implemented")
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        TODO("Not yet implemented")
    }

    override fun loadPayload(name: String): SerializationAction {
        TODO("Not yet implemented")
    }

    override fun deleteFile(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun normalizeCacheAndGetSessionFileIds(): List<String> {
        TODO("Not yet implemented")
    }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        TODO("Not yet implemented")
    }

    override fun writeSession(name: String, envelope: Envelope<SessionPayload>) {
        TODO("Not yet implemented")
    }

    override fun transformSession(name: String, transformer: (Envelope<SessionPayload>) -> Envelope<SessionPayload>) {
        TODO("Not yet implemented")
    }
}
