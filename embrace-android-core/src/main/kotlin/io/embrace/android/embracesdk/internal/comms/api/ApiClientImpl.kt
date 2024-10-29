package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream

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
internal class ApiClientImpl : ApiClient {

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
        action: SerializationAction,
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
        connection.setConnectTimeout(ApiClient.defaultTimeoutMs)
        connection.setReadTimeout(ApiClient.defaultTimeoutMs)
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
                HttpURLConnection.HTTP_OK -> {
                    val responseBody = readResponseBodyAsString(connection)
                    ApiResponse.Success(responseBody, responseHeaders)
                }

                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    ApiResponse.NotModified
                }

                HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> {
                    ApiResponse.PayloadTooLarge
                }

                ApiClient.TOO_MANY_REQUESTS -> {
                    val endpoint = connection.url.endpoint()
                    val retryAfter = responseHeaders["Retry-After"]?.toLongOrNull()
                    ApiResponse.TooManyRequests(endpoint, retryAfter)
                }

                ApiClient.NO_HTTP_RESPONSE -> {
                    ApiResponse.Incomplete(IllegalStateException("Connection failed or unexpected response code"))
                }

                else -> {
                    ApiResponse.Failure(responseCode, responseHeaders)
                }
            }
        } catch (exc: Throwable) {
            ApiResponse.Incomplete(
                IllegalStateException(
                    "Error occurred during HTTP request execution",
                    exc
                )
            )
        }
    }

    private fun readHttpResponseCode(connection: EmbraceConnection): Int {
        val responseCode: Int = try {
            connection.responseCode
        } catch (ex: IOException) {
            ApiClient.NO_HTTP_RESPONSE
        }
        return responseCode
    }

    private fun readHttpResponseHeaders(connection: EmbraceConnection): Map<String, String> {
        return connection.headerFields?.mapValues { it.value.joinToString() } ?: emptyMap()
    }

    /**
     * Reads the [InputStream] of a [EmbraceConnection] into a String. It will look at the connection to
     * see if it contains compressed data to determine whether it should be read into a gzip stream.
     */
    private fun readResponseBodyAsString(connection: EmbraceConnection): String {
        return try {
            val reader = if (connection.headerFields?.get("Content-Encoding")?.singleOrNull() == "gzip") {
                GZIPInputStream(connection.inputStream).bufferedReader()
            } else {
                InputStreamReader(connection.inputStream).buffered()
            }
            reader.use {
                it.readText()
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to read response body.", ex)
        }
    }
}
