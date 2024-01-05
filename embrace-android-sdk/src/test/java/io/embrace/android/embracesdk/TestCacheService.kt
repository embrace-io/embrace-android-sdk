package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.SessionMessage
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

internal class TestCacheService : CacheService {

    private val serializer = EmbraceSerializer()

    private val cache: MutableMap<String, ByteArray> = ConcurrentHashMap()

    override fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>) {
        cache[name] = serializer.toJson(objectToCache).toByteArray()
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        if (!cache.containsKey(name)) {
            return null
        }
        val json = String(checkNotNull(cache[name]))
        return serializer.fromJson(json, clazz)
    }

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        bytes?.let { cache[name] = it }
    }

    override fun loadBytes(name: String): ByteArray? {
        return cache[name]
    }

    override fun deleteFile(name: String): Boolean {
        return (cache.remove(name) != null)
    }

    override fun listFilenamesByPrefix(prefix: String): MutableList<String> {
        return (cache.keys.filter { it.startsWith(prefix) }).toMutableList()
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        cacheBytes(name, serializer.toJson(sessionMessage).toByteArray())
    }

    var oldPendingApiCalls: List<PendingApiCall>? = null

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? {
        return oldPendingApiCalls
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        val stream = ByteArrayOutputStream()
        action(stream)
        cache[name] = stream.toByteArray()
    }

    override fun loadPayload(name: String): SerializationAction {
        return {
            it.write(cache[name])
        }
    }
}
