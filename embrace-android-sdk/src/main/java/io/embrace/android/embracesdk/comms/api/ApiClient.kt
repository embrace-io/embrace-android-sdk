package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.zip.GZIPOutputStream

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
internal class ApiClient @JvmOverloads constructor(
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) {

    companion object {

        /**
         * The version of the API message format.
         */
        const val MESSAGE_VERSION = 13

        const val NO_HTTP_RESPONSE = -1
    }

    var timeoutMs = 60 * 1000

    private fun setTimeouts(connection: EmbraceConnection) {
        connection.setConnectTimeout(timeoutMs)
        connection.setReadTimeout(timeoutMs)
    }

    /**
     * Executes a GET request with the ApiRequest object, returning the response from the server
     * as a string.
     */
    @Throws(IllegalStateException::class)
    fun executeGet(request: ApiRequest): ApiResponse<String> {
        var connection: EmbraceConnection? = null

        try {
            connection = request.toConnection()
            setTimeouts(connection)
            connection.connect()
            return executeHttpRequest(connection)
        } catch (ex: Throwable) {
            throw IllegalStateException(ex.localizedMessage ?: "", ex)
        } finally {
            runCatching {
                connection?.inputStream?.close()
            }
        }
    }

    /**
     * Posts a payload according to the ApiRequest parameter. The payload will be gzip compressed.
     */
    fun post(request: ApiRequest, payload: ByteArray): String = rawPost(request, gzip(payload))

    /**
     * Posts a payload according to the ApiRequest parameter. The payload will not be gzip compressed.
     */
    fun rawPost(request: ApiRequest, payload: ByteArray?): String {
        logger.logDeveloper("ApiClient", request.httpMethod.toString() + " " + request.url)
        logger.logDeveloper("ApiClient", "Request details: $request")

        var connection: EmbraceConnection? = null
        return try {
            connection = request.toConnection()
            setTimeouts(connection)
            if (payload != null) {
                logger.logDeveloper("ApiClient", "Payload size: " + payload.size)
                connection.outputStream?.write(payload)
                connection.connect()
            }
            val response = executeHttpRequest(connection)

            // pre-existing behavior. handle this better in future.
            if (response.statusCode != HttpURLConnection.HTTP_OK) {
                @Suppress("UseCheckOrError") throw IllegalStateException("Failed to retrieve from Embrace server.")
            }
            response.body ?: ""
        } catch (ex: Throwable) {
            throw IllegalStateException(ex.localizedMessage ?: "", ex)
        } finally {
            runCatching {
                connection?.inputStream?.close()
            }
        }
    }

    /**
     * Executes a HTTP call using the specified connection, returning the response from the
     * server as a string.
     */
    private fun executeHttpRequest(connection: EmbraceConnection): ApiResponse<String> {
        try {
            val responseCode = readHttpResponseCode(connection)
            val headers = readHttpResponseHeaders(connection)
            return ApiResponse(
                responseCode,
                headers,
                readResponseBodyAsString(connection.inputStream)
            )
        } catch (exc: Throwable) {
            throw IllegalStateException("Error occurred during HTTP request execution", exc)
        }
    }

    private fun readHttpResponseCode(connection: EmbraceConnection): Int {
        var responseCode: Int? = null
        try {
            responseCode = connection.responseCode
            logger.logDeveloper("ApiClient", "Response status: $responseCode")
        } catch (ex: IOException) {
            logger.logDeveloper("ApiClient", "Connection failed or unexpected response code")
        }
        return responseCode ?: NO_HTTP_RESPONSE
    }

    private fun readHttpResponseHeaders(connection: EmbraceConnection): Map<String, String> {
        val headers = connection.headerFields?.mapValues { it.value.joinToString() } ?: emptyMap()

        headers.forEach { entry ->
            logger.logDeveloper("ApiClient", "Response header: ${entry.key}: ${entry.value}")
        }
        return headers
    }

    /**
     * Reads an [InputStream] into a String.
     *
     * @param inputStream the input stream to read
     * @return the string representation
     */
    private fun readResponseBodyAsString(inputStream: InputStream?): String {
        return try {
            val body = InputStreamReader(inputStream).buffered().use {
                it.readText()
            }
            logger.logDeveloper("ApiClient", "Successfully read response body.")
            body
        } catch (ex: IOException) {
            logger.logDeveloper("ApiClient", "Failed to read response body.", ex)
            throw IllegalStateException("Failed to read response body.", ex)
        }
    }

    /**
     * Compresses a given byte array using the GZIP compression algorithm.
     *
     * @param bytes the byte array to compress
     * @return the compressed byte array
     */
    private fun gzip(bytes: ByteArray): ByteArray {
        return try {
            ByteArrayOutputStream().use { baos ->
                GZIPOutputStream(baos).use { gzipStream ->
                    gzipStream.write(bytes)
                    gzipStream.finish()
                }
                baos.toByteArray()
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to gzip payload.", ex)
        }
    }
}
