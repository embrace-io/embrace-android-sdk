package io.embrace.android.gradle.plugin.network

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.buildreporter.BuildTelemetryRequest
import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeRequest
import io.embrace.android.gradle.plugin.tasks.ndk.NdkUploadHandshakeResponse
import io.embrace.android.gradle.plugin.util.serialization.EmbraceSerializer
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

const val HEADER_APP_ID = "X-EM-AID"
private const val KEY_APP_ID = "app"
private const val KEY_API_TOKEN = "token"
private const val KEY_BUILD_ID = "id"
private const val KEY_MAPPING_FILE = "file"
private const val KEY_VARIANT = "variant"
private const val KEY_ARCH = "arch"
private const val KEY_SYMBOL_ID = "id"
private const val KEY_FILENAME = "filename"

/**
 * This class acts as a wrapper for okHttp so the rest of the project is decoupled from the networking library
 * being used.
 */
class OkHttpNetworkService(
    private val baseUrl: String,
) : NetworkService {

    @Transient
    private val client = OkHttpClient().newBuilder()
        .writeTimeout(NETWORK_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val logger = Logger(OkHttpNetworkService::class.java)

    private val mediaTypeJson = "application/json".toMediaType()
    private val mediaTypeText = "text/plain".toMediaType()
    private val serializer: ThreadLocal<EmbraceSerializer> =
        object : ThreadLocal<EmbraceSerializer>() {
            override fun initialValue(): EmbraceSerializer {
                return MoshiSerializer()
            }
        }
    private val defaultBodyDeserializer = { stream: InputStream ->
        stream.bufferedReader().readText()
    }

    override fun postBuildTelemetry(request: BuildTelemetryRequest): HttpCallResult {
        return makePostRequest<BuildTelemetryRequest, String>(
            endpoint = EmbraceEndpoint.BUILD_DATA,
            payload = request,
            deserializationAction = defaultBodyDeserializer
        )
    }

    override fun postNdkHandshake(
        appId: String,
        handshake: NdkUploadHandshakeRequest
    ): HttpCallResult {
        return makePostRequest<NdkUploadHandshakeRequest, NdkUploadHandshakeResponse>(
            endpoint = EmbraceEndpoint.NDK_HANDSHAKE,
            payload = handshake,
            appId = appId,
        ) { serializer.get().fromJson(it, NdkUploadHandshakeResponse::class.java) }
    }

    override fun uploadNdkSymbolFile(
        params: RequestParams,
        file: File,
        variantName: String,
        arch: String,
        id: String
    ): HttpCallResult {
        return makeMultipartRequest(params, file) {
            addFormDataPart(KEY_VARIANT, variantName)
            addFormDataPart(KEY_ARCH, arch)
            addFormDataPart(KEY_SYMBOL_ID, id)
            params.fileName?.let {
                addFormDataPart(KEY_FILENAME, params.fileName)
            }
        }
    }

    override fun uploadRnSourcemapFile(params: RequestParams, file: File): HttpCallResult {
        return makeMultipartRequest(params, file)
    }

    override fun uploadFile(params: RequestParams, file: File): HttpCallResult {
        return makeMultipartRequest(params, file)
    }

    private fun makeMultipartRequest(
        params: RequestParams,
        file: File,
        action: MultipartBody.Builder.() -> Unit = {}
    ): HttpCallResult {
        return makeRequest(
            requestProvider = {
                val requestBody = prepareCommonMultipartBody(params, file)
                action(requestBody)
                prepareCommonRequest(params.endpoint, params.appId)
                    .post(requestBody.build())
                    .build()
            },
            deserializationAction = defaultBodyDeserializer
        )
    }

    private inline fun <reified T, reified O> makePostRequest(
        endpoint: EmbraceEndpoint,
        payload: T,
        appId: String? = null,
        deserializationAction: (stream: InputStream) -> O
    ): HttpCallResult {
        val body = StreamedRequestBody(mediaTypeJson) {
            serializer.get().toJson(payload, T::class.java, it)
        }
        return makeRequest<O>(
            requestProvider = {
                prepareCommonRequest(endpoint, appId)
                    .post(body)
                    .build()
            },
            deserializationAction = deserializationAction
        )
    }

    private fun prepareCommonRequest(
        endpoint: EmbraceEndpoint,
        appId: String?,
    ): Request.Builder {
        val builder = Request.Builder()
        builder.url("$baseUrl${endpoint.url}")
        appId?.isNotBlank()?.let {
            builder.addHeader(HEADER_APP_ID, appId)
        }
        return builder
    }

    private inline fun <reified O> makeRequest(
        requestProvider: () -> Request,
        deserializationAction: (stream: InputStream) -> O,
    ): HttpCallResult {
        return try {
            val request = requestProvider()
            client.newCall(request).execute().use { response ->
                val result = handleServerResponse<O>(response, deserializationAction)
                logHttpCallResult(result)
                result
            }
        } catch (exc: Exception) {
            logger.error("Exception occurred while making network request", exc)
            HttpCallResult.Error(exc)
        }
    }

    private fun logHttpCallResult(result: HttpCallResult) {
        when (result) {
            is HttpCallResult.Success<*> -> {
                logger.info("Request succeeded code=${result.code}, body=${result.body}")
            }

            is HttpCallResult.Failure -> {
                logger.error("Request failed: code=${result.code}, message=${result.errorMessage}")
            }

            is HttpCallResult.Error -> {
                logger.error(
                    "Failed to make request",
                    result.exception
                )
            }
        }
    }

    private inline fun <reified O> handleServerResponse(
        response: Response,
        deserializationAction: (stream: InputStream) -> O
    ): HttpCallResult {
        val result = when {
            response.isSuccessful -> {
                val payload =
                    response.body?.byteStream()?.buffered()?.use(deserializationAction)
                HttpCallResult.Success(payload, response.code)
            }

            else -> {
                HttpCallResult.Failure(response.body?.string(), response.code)
            }
        }
        return result
    }

    private fun prepareCommonMultipartBody(
        params: RequestParams,
        file: File
    ): MultipartBody.Builder {
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(KEY_APP_ID, params.appId)
            .addFormDataPart(KEY_API_TOKEN, params.apiToken)
            .apply {
                if (!params.buildId.isNullOrBlank()) {
                    addFormDataPart(KEY_BUILD_ID, params.buildId)
                }
            }
            .addFormDataPart(
                KEY_MAPPING_FILE,
                params.fileName,
                file.asRequestBody(mediaTypeText)
            )
    }

    companion object {
        private const val NETWORK_WRITE_TIMEOUT_SECONDS = 120L
    }
}
