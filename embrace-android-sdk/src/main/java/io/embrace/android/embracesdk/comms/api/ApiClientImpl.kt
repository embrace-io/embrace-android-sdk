package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.comms.api.ApiClient.Companion.NO_HTTP_RESPONSE
import io.embrace.android.embracesdk.comms.api.ApiClient.Companion.TOO_MANY_REQUESTS
import io.embrace.android.embracesdk.comms.api.ApiClient.Companion.defaultTimeoutMs
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.logging.EmbLogger
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK

/**
 * Client for calling the Embrace API. This service handles all calls to the Embrace API.
 *
 * Sessions can be sent to either the production or development endpoint. The development
 * endpoint shows sessions on the 'integration testing' screen within the dashboard, whereas
 * the production endpoint sends sessions to 'recent sessions'.
 *
 * The development endpoint is only used if the build is a debug build, and if integration
 * testing is enabled when calling [Embrace.start()].
 */
internal class ApiClientImpl(
    private val logger: EmbLogger
) : ApiClient {

    override fun executeGet(request: ApiRequest): ApiResponse {
        var connection: EmbraceConnection? = null

        return try {
            connection = request.toConnection()
            setTimeouts(connection)
            connection.connect()
            val response = executeHttpRequest(connection)
            response
        } catch (ex: Throwable) {
            ApiResponse.Incomplete(IllegalStateException(ex.localizedMessage ?: "", ex))
        } finally {
            runCatching {
                connection?.inputStream?.close()
            }
        }
    }

    override fun executePost(
        request: ApiRequest,
        action: SerializationAction
    ): ApiResponse {
        var connection: EmbraceConnection? = null
        return try {
            connection = request.toConnection()
            setTimeouts(connection)

            connection.outputStream?.use(action)
            connection.connect()
            val response = executeHttpRequest(connection)
            response
        } catch (ex: Throwable) {
            ApiResponse.Incomplete(IllegalStateException(ex.localizedMessage ?: "", ex))
        } finally {
            runCatching {
                connection?.inputStream?.close()
            }
        }
    }

    private fun setTimeouts(connection: EmbraceConnection) {
        connection.setConnectTimeout(defaultTimeoutMs)
        connection.setReadTimeout(defaultTimeoutMs)
    }

    /**
     * Executes a HTTP call using the specified connection, returning the response from the
     * server as a string.
     */
    private fun executeHttpRequest(connection: EmbraceConnection): ApiResponse {
        return try {
            val responseCode = readHttpResponseCode(connection)
            val responseHeaders = readHttpResponseHeaders(connection)

            return when (responseCode) {
                HTTP_OK -> {
                    val responseBody = readResponseBodyAsString(connection.inputStream)
                    ApiResponse.Success(responseBody, responseHeaders)
                }

                HTTP_NOT_MODIFIED -> {
                    ApiResponse.NotModified
                }

                HTTP_ENTITY_TOO_LARGE -> {
                    ApiResponse.PayloadTooLarge
                }

                TOO_MANY_REQUESTS -> {
                    val endpoint = connection.url.endpoint()
                    val retryAfter = responseHeaders["Retry-After"]?.toLongOrNull()
                    ApiResponse.TooManyRequests(endpoint, retryAfter)
                }

                NO_HTTP_RESPONSE -> {
                    ApiResponse.Incomplete(IllegalStateException("Connection failed or unexpected response code"))
                }

                else -> {
                    ApiResponse.Failure(responseCode, responseHeaders)
                }
            }
        } catch (exc: Throwable) {
            ApiResponse.Incomplete(IllegalStateException("Error occurred during HTTP request execution", exc))
        }
    }

    private fun readHttpResponseCode(connection: EmbraceConnection): Int {
        var responseCode: Int? = null
        try {
            responseCode = connection.responseCode
        } catch (ex: IOException) {
            logger.logInfo("Connection failed or unexpected response code")
        }
        return responseCode ?: NO_HTTP_RESPONSE
    }

    private fun readHttpResponseHeaders(connection: EmbraceConnection): Map<String, String> {
        return connection.headerFields?.mapValues { it.value.joinToString() } ?: emptyMap()
    }

    /**
     * Reads an [InputStream] into a String.
     *
     * @param inputStream the input stream to read
     * @return the string representation
     */
    private fun readResponseBodyAsString(inputStream: InputStream?): String {
        return try {
            InputStreamReader(inputStream).buffered().use {
                it.readText()
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to read response body.", ex)
        }
    }
}
