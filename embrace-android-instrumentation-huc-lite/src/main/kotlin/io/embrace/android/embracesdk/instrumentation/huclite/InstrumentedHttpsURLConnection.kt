package io.embrace.android.embracesdk.instrumentation.huclite

import android.os.Build
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.network.logging.getOverriddenURLString
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.io.InputStream
import java.io.OutputStream
import java.security.Permission
import java.security.Principal
import java.security.cert.Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * A wrapper around [HttpsURLConnection] that defers all function calls to the wrapped connection and handles instrumentation as it
 * goes through its lifecycle
 */
internal class InstrumentedHttpsURLConnection(
    private val wrappedConnection: HttpsURLConnection,
    private val sdkStateApi: SdkStateApi,
    private val instrumentationApi: InstrumentationApi,
    private val networkRequestApi: NetworkRequestApi,
    private val internalInterface: EmbraceInternalInterface,
) : HttpsURLConnection(wrappedConnection.url) {

    private val requestTriggered = AtomicBoolean(false)
    private val cachedResponseStatusCode: AtomicInteger = AtomicInteger(0)
    private val requestData: RequestData? =
        if (sdkStateApi.isStarted) {
            runCatching {
                RequestData(
                    connection = wrappedConnection,
                    now = instrumentationApi::getSdkCurrentTimeMs,
                    trackRequest = sdkStateApi::isStarted,
                    networkRequestApi = networkRequestApi,
                    errorHandler = internalInterface::logInternalError
                )
            }.onFailure {
                internalInterface.logInternalError(InstrumentationException(it))
            }.getOrNull()
        } else {
            null
        }

    // Overridden methods that will return when a TCP connection has been established without sending the request

    override fun connect() {
        requestData?.startRequest()
        try {
            wrappedConnection.connect()
        } catch (t: Throwable) {
            requestData?.clientError(t)
            throw t
        }
    }

    override fun getOutputStream(): OutputStream {
        requestData?.startRequest()
        try {
            return wrappedConnection.outputStream
        } catch (t: Throwable) {
            requestData?.clientError(t)
            throw t
        }
    }

    // Overridden methods that will trigger a request that will be instrumented

    override fun getResponseCode(): Int =
        invokeRequestTriggeringMethod {
            wrappedConnection.responseCode
        }

    override fun getResponseMessage(): String? {
        return invokeRequestTriggeringMethod<String?> {
            wrappedConnection.responseMessage
        }
    }

    override fun getHeaderField(n: Int): String? =
        invokeRequestTriggeringMethod<String?> {
            wrappedConnection.getHeaderField(n)
        }

    override fun getHeaderFieldKey(n: Int): String? =
        invokeRequestTriggeringMethod<String?> {
            wrappedConnection.getHeaderFieldKey(n)
        }

    override fun getHeaderField(name: String?): String? =
        invokeRequestTriggeringMethod {
            wrappedConnection.getHeaderField(name)
        }

    override fun getHeaderFields(): MutableMap<String, MutableList<String>> =
        invokeRequestTriggeringMethod {
            wrappedConnection.headerFields
        }

    override fun getInputStream(): InputStream =
        invokeRequestTriggeringMethod {
            wrappedConnection.inputStream
        }

    override fun getErrorStream(): InputStream? =
        invokeRequestTriggeringMethod {
            wrappedConnection.errorStream
        }

    override fun getContent(): Any =
        invokeRequestTriggeringMethod {
            wrappedConnection.content
        }

    override fun getContent(classes: Array<out Class<*>>?): Any =
        invokeRequestTriggeringMethod {
            wrappedConnection.getContent(classes)
        }

    override fun getHeaderFieldInt(name: String?, default: Int): Int =
        invokeRequestTriggeringMethod {
            wrappedConnection.getHeaderFieldInt(name, default)
        }

    override fun getHeaderFieldLong(name: String?, default: Long): Long =
        invokeRequestTriggeringMethod {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wrappedConnection.getHeaderFieldLong(name, default)
            } else {
                wrappedConnection.getHeaderFieldInt(name, default.toInt()).toLong()
            }
        }

    override fun getHeaderFieldDate(name: String?, default: Long): Long =
        invokeRequestTriggeringMethod {
            wrappedConnection.getHeaderFieldDate(name, default)
        }

    override fun getContentEncoding(): String? =
        invokeRequestTriggeringMethod {
            wrappedConnection.contentEncoding
        }

    override fun getContentLength(): Int =
        invokeRequestTriggeringMethod {
            wrappedConnection.contentLength
        }

    override fun getContentLengthLong(): Long =
        invokeRequestTriggeringMethod {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wrappedConnection.contentLengthLong
            } else {
                wrappedConnection.contentLength.toLong()
            }
        }

    override fun getContentType(): String? =
        invokeRequestTriggeringMethod {
            wrappedConnection.contentType
        }

    override fun getDate(): Long =
        invokeRequestTriggeringMethod {
            wrappedConnection.date
        }

    override fun getExpiration(): Long =
        invokeRequestTriggeringMethod {
            wrappedConnection.expiration
        }

    override fun getLastModified(): Long =
        invokeRequestTriggeringMethod {
            wrappedConnection.lastModified
        }

    // Pass-through methods from HttpsURLConnection that won't be instrumented directly

    override fun getCipherSuite(): String? = wrappedConnection.cipherSuite

    override fun getLocalCertificates(): Array<out Certificate?>? = wrappedConnection.localCertificates

    override fun getServerCertificates(): Array<out Certificate?>? = wrappedConnection.serverCertificates

    override fun getHostnameVerifier(): HostnameVerifier? = wrappedConnection.hostnameVerifier

    override fun setHostnameVerifier(verifier: HostnameVerifier?) {
        wrappedConnection.hostnameVerifier = verifier
    }

    override fun getSSLSocketFactory(): SSLSocketFactory? = wrappedConnection.sslSocketFactory

    override fun setSSLSocketFactory(factory: SSLSocketFactory?) {
        wrappedConnection.sslSocketFactory = factory
    }

    override fun getLocalPrincipal(): Principal? = wrappedConnection.localPrincipal

    override fun getPeerPrincipal(): Principal? = wrappedConnection.peerPrincipal

    // Pass-through methods from HttpURLConnection that won't be instrumented directly

    override fun disconnect() = wrappedConnection.disconnect()

    override fun usingProxy(): Boolean = wrappedConnection.usingProxy()

    override fun getRequestMethod(): String = wrappedConnection.requestMethod

    override fun setRequestMethod(method: String?) {
        wrappedConnection.requestMethod = method
    }

    override fun setFixedLengthStreamingMode(contentLengthLong: Long) {
        wrappedConnection.setFixedLengthStreamingMode(contentLengthLong)
    }

    override fun setFixedLengthStreamingMode(contentLengthInt: Int) {
        wrappedConnection.setFixedLengthStreamingMode(contentLengthInt)
    }

    override fun setChunkedStreamingMode(mode: Int) {
        wrappedConnection.setChunkedStreamingMode(mode)
    }

    override fun getInstanceFollowRedirects(): Boolean = wrappedConnection.instanceFollowRedirects

    override fun setInstanceFollowRedirects(followRedirects: Boolean) {
        wrappedConnection.instanceFollowRedirects = followRedirects
    }

    override fun getPermission(): Permission? = wrappedConnection.permission

    // Pass-through methods from URLConnection that won't be instrumented directly

    override fun addRequestProperty(key: String?, value: String?) {
        wrappedConnection.addRequestProperty(key, value)
    }

    override fun getRequestProperty(key: String?): String? = wrappedConnection.getRequestProperty(key)

    override fun setRequestProperty(key: String?, value: String?) {
        wrappedConnection.setRequestProperty(key, value)
    }

    override fun getRequestProperties(): MutableMap<String, MutableList<String>> = wrappedConnection.requestProperties

    override fun setDoInput(doinput: Boolean) {
        wrappedConnection.doInput = doinput
    }

    override fun getDoInput(): Boolean = wrappedConnection.doInput

    override fun setDoOutput(dooutput: Boolean) {
        wrappedConnection.doOutput = dooutput
    }

    override fun getDoOutput(): Boolean = wrappedConnection.doOutput

    override fun setAllowUserInteraction(allowuserinteraction: Boolean) {
        wrappedConnection.allowUserInteraction = allowuserinteraction
    }

    override fun getAllowUserInteraction(): Boolean = wrappedConnection.allowUserInteraction

    override fun setUseCaches(useCaches: Boolean) {
        wrappedConnection.useCaches = useCaches
    }

    override fun getUseCaches(): Boolean = wrappedConnection.useCaches

    override fun setIfModifiedSince(ifModifiedSince: Long) {
        wrappedConnection.ifModifiedSince = ifModifiedSince
    }

    override fun getIfModifiedSince(): Long = wrappedConnection.ifModifiedSince

    override fun getDefaultUseCaches(): Boolean = wrappedConnection.defaultUseCaches

    override fun setDefaultUseCaches(defaultUseCaches: Boolean) {
        wrappedConnection.defaultUseCaches = defaultUseCaches
    }

    override fun setConnectTimeout(timeout: Int) {
        wrappedConnection.connectTimeout = timeout
    }

    override fun getConnectTimeout(): Int = wrappedConnection.connectTimeout

    override fun setReadTimeout(timeout: Int) {
        wrappedConnection.readTimeout = timeout
    }

    override fun getReadTimeout(): Int = wrappedConnection.readTimeout

    private fun <T> invokeRequestTriggeringMethod(wrappedMethod: () -> T): T {
        if (!sdkStateApi.isStarted) {
            return wrappedMethod()
        }

        val instrument = requestTriggered.compareAndSet(false, true)
        if (instrument) {
            requestData?.startRequest()
        }
        val returnValue = runCatching {
            wrappedMethod().also {
                if (cachedResponseStatusCode.get() == 0) {
                    cachedResponseStatusCode.compareAndSet(0, wrappedConnection.responseCode)
                }
            }
        }.onFailure {
            if (instrument) {
                requestData?.clientError(it)
            }
        }.getOrThrow()

        if (instrument) {
            requestData?.completeRequest(cachedResponseStatusCode.get())
        }
        return returnValue
    }

    private class RequestData(
        connection: HttpsURLConnection,
        val now: () -> Long,
        val trackRequest: () -> Boolean,
        val networkRequestApi: NetworkRequestApi,
        val errorHandler: (t: Throwable) -> Unit,
    ) {
        private val creationTimeMs = now()
        private val telemetryUrlProvider = {
            getOverriddenURLString(
                request = HucLitePathOverrideRequest(
                    requestHeaderProvider = connection::getRequestProperty,
                    originalUrl = connection.url
                )
            )
        }
        private val methodProvider = {
            HttpMethod.fromString(connection.requestMethod)
        }
        private val startTimeMs = AtomicLong(INVALID_START_TIME)
        private val requestRecorded = AtomicBoolean(false)

        fun startRequest(timestampMs: Long = now()) =
            runCatching {
                startTimeMs.compareAndSet(INVALID_START_TIME, timestampMs)
            }.onFailure {
                errorHandler(InstrumentationException(it))
            }

        fun completeRequest(responseCode: Int) {
            recordRequest {
                val endTimeMs = now()
                networkRequestApi.recordNetworkRequest(
                    networkRequest = EmbraceNetworkRequest.fromCompletedRequest(
                        url = telemetryUrlProvider(),
                        httpMethod = methodProvider(),
                        startTime = getValidStartTime(),
                        endTime = endTimeMs,
                        bytesSent = 0,
                        bytesReceived = 0,
                        statusCode = responseCode,
                    )
                )
            }
        }

        fun clientError(t: Throwable) {
            recordRequest {
                val errorTimeMs = now()
                networkRequestApi.recordNetworkRequest(
                    networkRequest = EmbraceNetworkRequest.fromIncompleteRequest(
                        url = telemetryUrlProvider(),
                        httpMethod = methodProvider(),
                        startTime = getValidStartTime(),
                        endTime = errorTimeMs,
                        errorType = t::class.java.canonicalName ?: t::class.java.simpleName,
                        errorMessage = t.message ?: "Unexpected error"
                    )
                )
            }
        }

        private fun getValidStartTime(): Long =
            if (startTimeMs.get() == INVALID_START_TIME) {
                creationTimeMs
            } else {
                startTimeMs.get()
            }

        private fun recordRequest(recordingFunction: () -> Unit) {
            runCatching {
                if (trackRequest() && requestRecorded.compareAndSet(false, true)) {
                    recordingFunction()
                }
            }.onFailure {
                errorHandler(it)
            }
        }

        private companion object {
            const val INVALID_START_TIME = -1L
        }
    }

    private class InstrumentationException(
        cause: Throwable,
    ) : RuntimeException("Unexpected error during HUC instrumentation", cause)
}
