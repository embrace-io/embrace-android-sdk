package io.embrace.android.embracesdk

import com.google.gson.GsonBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceUrl
import io.embrace.android.embracesdk.comms.api.EmbraceUrlAdapter
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.payload.SessionMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal class TestCacheService : CacheService {

    private val gson = GsonBuilder()
        .registerTypeAdapter(EmbraceUrl::class.java, EmbraceUrlAdapter())
        .create()

    private val cache: MutableMap<String, ByteArray> = ConcurrentHashMap()

    override fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>) {
        cache[name] = gson.toJson(objectToCache, clazz.genericSuperclass).toByteArray()
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? {
        if (!cache.containsKey(name)) {
            return null
        }
        val json = String(checkNotNull(cache[name]))
        return gson.fromJson(json, clazz)
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

    override fun deleteObject(name: String): Boolean {
        return cache.remove(name) != null
    }

    override fun deleteObjectsByRegex(regex: String): Boolean {
        val pattern = Pattern.compile(regex)
        var result = false
        for (key in cache.keys) {
            if (pattern.matcher(key).find()) {
                cache.remove(key)
                result = true
            }
        }
        return result
    }

    override fun moveObject(src: String, dst: String): Boolean {
        val bytes = cache[src] ?: return false
        cache[dst] = bytes
        cache.remove(src)
        return true
    }

    override fun listFilenamesByPrefix(prefix: String): MutableList<String> {
        return (cache.keys.filter { it.startsWith(prefix) }).toMutableList()
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        cacheBytes(name, gson.toJson(sessionMessage).toByteArray())
    }
}
