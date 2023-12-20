package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.payload.SessionMessage

internal class FakeCacheService : CacheService {
    override fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>) {
        TODO("Not yet implemented")
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        TODO("Not yet implemented")
    }

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        TODO("Not yet implemented")
    }

    override fun loadBytes(name: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun deleteFile(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun deleteObject(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun deleteObjectsByRegex(regex: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun moveObject(src: String, dst: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun listFilenamesByPrefix(prefix: String): List<String>? {
        TODO("Not yet implemented")
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        TODO("Not yet implemented")
    }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        TODO("Not yet implemented")
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        TODO("Not yet implemented")
    }

    override fun loadPayload(name: String): SerializationAction {
        TODO("Not yet implemented")
    }
}
