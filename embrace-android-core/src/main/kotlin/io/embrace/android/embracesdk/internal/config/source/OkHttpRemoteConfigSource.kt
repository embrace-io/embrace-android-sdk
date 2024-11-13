package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiRequestUrl
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.comms.api.getHeaders
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.network.http.HttpMethod
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.GzipSource
import okio.buffer
import java.io.IOException

internal class OkHttpRemoteConfigSource(
    private val okhttpClient: OkHttpClient,
    private val apiUrlBuilder: ApiUrlBuilder,
    private val serializer: PlatformSerializer,
) : RemoteConfigSource {

    override fun getConfig(): RemoteConfig? = try {
        fetchConfigImpl()
    } catch (exc: IOException) {
        null
    }

    override fun setInitialEtag(etag: String) {
        this.etag = etag
    }

    private var etag: String? = null

    private fun fetchConfigImpl(): RemoteConfig? {
        val request = prepareRequest()
        val call = okhttpClient.newCall(request)
        val response = call.execute()
        return processResponse(response)
    }

    private fun prepareRequest(): Request {
        val url = apiUrlBuilder.resolveUrl(Endpoint.CONFIG)
        val headers = prepareConfigRequest(url).getHeaders()
        val builder = Request.Builder().url(url)

        etag?.let {
            builder.header("If-None-Match", it)
        }
        headers.forEach { entry ->
            builder.header(entry.key, entry.value)
        }
        val request = builder.build()
        return request
    }

    private fun processResponse(response: Response): RemoteConfig? {
        response.header("ETag")?.let {
            this.etag = it
        }
        if (!response.isSuccessful) {
            return null
        }
        return response.body?.source()?.use { src ->
            val gzipSource = GzipSource(src)
            gzipSource.buffer().inputStream().use {
                serializer.fromJson(it, RemoteConfig::class.java)
            }
        }
    }

    private fun prepareConfigRequest(url: String) = ApiRequest(
        userAgent = "Embrace/a/" + BuildConfig.VERSION_NAME,
        url = ApiRequestUrl(url),
        httpMethod = HttpMethod.GET,
        acceptEncoding = "gzip",
        appId = apiUrlBuilder.appId,
        deviceId = apiUrlBuilder.deviceId,
    )
}
