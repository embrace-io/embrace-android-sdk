package io.embrace.android.embracesdk.internal.comms.api

import android.net.http.HttpResponseCache
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.StorageService
import java.io.Closeable
import java.io.IOException
import java.net.CacheResponse
import java.net.URI

/**
 * Caches HTTP requests made via HttpUrlConnection using [HttpResponseCache]. This is
 * currently only used to cache responses from the config endpoint, which contain etags in the
 * response headers.
 *
 * This class therefore provides functions to retrieve the etag for any cached responses. This
 * means the eTag can be set in the request header & we can avoid unnecessary work on the client
 * & on the server.
 */
class ApiResponseCache(
    private val serializer: PlatformSerializer,
    private val storageService: StorageService,
    private val logger: EmbLogger
) : Closeable {

    private companion object {
        private const val MAX_CACHE_SIZE_BYTES: Long = 2 * 1024 * 1024 // 2 MiB
        private const val ETAG_HEADER = "ETag"
    }

    @Volatile
    private var cache: HttpResponseCache? = null
    private val lock = Object()

    private fun initializeIfNeeded() {
        if (cache == null) {
            synchronized(lock) {
                if (cache == null) {
                    cache = try {
                        HttpResponseCache.install(
                            storageService.getConfigCacheDir(),
                            MAX_CACHE_SIZE_BYTES
                        )
                    } catch (exc: IOException) {
                        logger.logWarning("Failed to initialize HTTP cache.", exc)
                        null
                    }
                }
            }
        }
    }

    override fun close() {
        cache?.flush()
    }

    fun retrieveCachedConfig(url: String, request: ApiRequest): CachedConfig {
        val cachedResponse = retrieveCacheResponse(url, request)
        val obj = cachedResponse?.runCatching {
            serializer.fromJson(body, RemoteConfig::class.java)
        }?.getOrNull()
        val eTag = cachedResponse?.let { retrieveETag(cachedResponse) }
        return CachedConfig(obj, eTag)
    }

    /**
     * Retrieves the cache response for the given request, if any exists.
     */
    private fun retrieveCacheResponse(url: String, request: ApiRequest): CacheResponse? {
        initializeIfNeeded()
        val obj = cache ?: return null

        return try {
            val uri = URI.create(url)
            val requestMethod = request.httpMethod.toString()
            val headerFields = request.getHeaders().mapValues { listOf(it.value) }
            obj.get(uri, requestMethod, headerFields)
        } catch (exc: IOException) {
            null
        }
    }

    /**
     * Searches the cache to see whether a request has a cached response, and if so returns its etag.
     */
    private fun retrieveETag(cacheResponse: CacheResponse): String? {
        try {
            val eTag = cacheResponse.headers[ETAG_HEADER]
            if (!eTag.isNullOrEmpty()) {
                return eTag[0]
            }
        } catch (exc: IOException) {
            logger.logWarning("Failed to find ETag", exc)
        }
        return null
    }
}
