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

    private fun fetchConfigImpl(): RemoteConfig? {
        val url = apiUrlBuilder.resolveUrl(Endpoint.CONFIG)
        val headers = prepareConfigRequest(url).getHeaders()
        val builder = Request.Builder().url(url)

        headers.forEach { entry ->
            builder.header(entry.key, entry.value)
        }
        val call = okhttpClient.newCall(
            builder.build()
        )

        val response = call.execute()
        if (!response.isSuccessful) {
            return null
        }
        val config = response.body?.source()?.use { src ->
            val gzipSource = GzipSource(src)
            gzipSource.buffer().inputStream().use {
                serializer.fromJson(it, RemoteConfig::class.java)
            }
        }
        return config
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
