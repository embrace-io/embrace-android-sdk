package io.embrace.android.embracesdk.internal.config.source

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.GzipSource
import okio.buffer
import java.io.IOException

class OkHttpRemoteConfigSource internal constructor(
    private val okhttpClient: Lazy<OkHttpClient>,
    private val serializer: PlatformSerializer,
    configEndpoint: Lazy<ConfigEndpoint>,
    private val errorHandler: ErrorHandler,
) : RemoteConfigSource {

    /**
     * The endpoint is resolved on the first request, so constructing the source neither reads the
     * device ID nor requires an app ID.
     */
    constructor(
        okhttpClient: Lazy<OkHttpClient>,
        serializer: PlatformSerializer,
        instrumentedConfig: InstrumentedConfig,
        deviceIdProvider: () -> String,
        sdkVersion: String,
        apiLevel: Int,
        errorHandler: ErrorHandler,
    ) : this(
        okhttpClient,
        serializer,
        lazy {
            ConfigEndpoint(
                deviceIdProvider(),
                instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
                instrumentedConfig,
                sdkVersion,
                apiLevel,
            )
        },
        errorHandler,
    )

    private val configEndpoint: ConfigEndpoint by configEndpoint

    override fun getConfig(): ConfigHttpResponse? = try {
        fetchConfigImpl()
    } catch (_: IOException) {
        null
    } catch (exc: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.RemoteConfigFetchFail, exc)
        null
    }

    override fun setInitialEtag(etag: String) {
        this.etag = etag
    }

    private var etag: String? = null

    private fun fetchConfigImpl(): ConfigHttpResponse? {
        val request = prepareRequest()
        val call = okhttpClient.value.newCall(request)
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

    private fun processResponse(response: Response): ConfigHttpResponse? = response.use {
        response.header("etag")?.let { etagHeader ->
            this.etag = etagHeader
        }
        if (!response.isSuccessful) {
            return null
        }
        val cfg = try {
            response.body?.source()?.use { src ->
                val gzipSource = GzipSource(src)
                gzipSource.buffer().inputStream().use { stream ->
                    serializer.fromJson(stream, RemoteConfig.serializer())
                }
            }
        } catch (exc: IOException) {
            throw exc
        } catch (exc: Throwable) {
            errorHandler.trackSdkError(SdkErrorType.RemoteConfigParseFail, exc)
            return null
        }
        ConfigHttpResponse(cfg, etag)
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
