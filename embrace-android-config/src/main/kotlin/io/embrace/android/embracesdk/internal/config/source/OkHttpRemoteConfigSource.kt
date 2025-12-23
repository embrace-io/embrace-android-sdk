package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.GzipSource
import okio.buffer
import java.io.IOException

class OkHttpRemoteConfigSource(
    private val okhttpClient: OkHttpClient,
    private val serializer: PlatformSerializer,
    private val configEndpoint: ConfigEndpoint,
) : RemoteConfigSource {

    override fun getConfig(): ConfigHttpResponse? = try {
        fetchConfigImpl()
    } catch (exc: IOException) {
        null
    }

    override fun setInitialEtag(etag: String) {
        this.etag = etag
    }

    private var etag: String? = null

    private fun fetchConfigImpl(): ConfigHttpResponse? {
        val request = prepareRequest()
        val call = okhttpClient.newCall(request)
        val response = call.execute()
        return processResponse(response)
    }

    private fun prepareRequest(): Request {
        val url = configEndpoint.url
        val headers = prepareConfigRequestHeaders()
        val builder = Request.Builder().url(url)

        headers.forEach { entry ->
            builder.header(entry.key, entry.value)
        }
        val request = builder.build()
        return request
    }

    private fun processResponse(response: Response): ConfigHttpResponse? {
        response.header("etag")?.let {
            this.etag = it
        }
        if (!response.isSuccessful) {
            return null
        }
        val cfg = response.body?.source()?.use { src ->
            val gzipSource = GzipSource(src)
            gzipSource.buffer().inputStream().use {
                serializer.fromJson(it, RemoteConfig::class.java)
            }
        }
        return ConfigHttpResponse(cfg, etag)
    }

    private fun prepareConfigRequestHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "User-Agent" to "Embrace/a/" + configEndpoint.sdkVersion,
            "Content-Type" to "application/json",
            "X-EM-AID" to configEndpoint.appId,
            "X-EM-DID" to configEndpoint.deviceId,
            "Accept-Encoding" to "gzip",
        )
        etag?.let {
            headers["If-None-Match"] = it
        }
        return headers
    }
}
