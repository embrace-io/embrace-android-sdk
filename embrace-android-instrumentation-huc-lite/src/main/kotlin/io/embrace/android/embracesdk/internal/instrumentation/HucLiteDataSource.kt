package io.embrace.android.embracesdk.internal.instrumentation

import android.annotation.SuppressLint
import android.os.Build
import io.embrace.android.embracesdk.instrumentation.huclite.DelegatingInstrumentedURLStreamHandlerFactory
import io.embrace.android.embracesdk.instrumentation.huclite.HucLitePathOverrideRequest
import io.embrace.android.embracesdk.instrumentation.huclite.InstrumentedUrlStreamHandlerFactory
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.network.getOverriddenURLString
import io.embrace.android.embracesdk.internal.instrumentation.network.toStatusCodeString
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.network.http.HttpRequestInfoModifierChain
import io.embrace.android.embracesdk.internal.network.http.MutableHttpRequestInfoImpl
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.NetworkUtils
import io.embrace.android.embracesdk.network.http.HttpRequestInfo
import io.opentelemetry.kotlin.semconv.ErrorAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.UrlAttributes
import io.opentelemetry.kotlin.semconv.UserAgentAttributes
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

class HucLiteDataSource(
    private val args: InstrumentationArgs,
    private val streamHandlerFactoryFieldProvider: () -> Field? = ::defaultFactoryFieldProvider,
    private val factoryInstaller: (URLStreamHandlerFactory) -> Unit = URL::setURLStreamHandlerFactory,
    private val httpsHandlerClassProvider: () -> Class<*> = ::defaultHttpsHandlerClass,
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
    instrumentationName = "huc_lite_data_source",
) {
    private val telemetryDestination = args.destination
    private val domainCountLimiter = args.configService.networkBehavior.domainCountLimiter
    private val recordForUrl = args.configService.networkBehavior::isUrlEnabled
    private var initializationAttempted = false

    private val dataSourceRef = AtomicReference<HucLiteDataSource?>(this)

    override fun onDataCaptureEnabled() {
        dataSourceRef.set(this)
        if (!initializationAttempted) {
            try {
                installURLStreamHandlerFactory(
                    clock = args.clock,
                    errorHandler = args.errorHandler,
                )
            } finally {
                initializationAttempted = true
            }
        }
    }

    override fun onDataCaptureDisabled() {
        dataSourceRef.set(null)
    }

    fun createRequestData(
        wrappedConnection: HttpsURLConnection,
        clock: Clock,
    ): RequestData? {
        return if (recordForUrl(wrappedConnection.url.toString())) {
            runCatching {
                RequestData(
                    connection = wrappedConnection,
                    shouldRecord = domainCountLimiter::canLogNetworkRequest,
                    onLimitReached = {
                        telemetryService.trackAppliedLimit("huc_network_request", AppliedLimitType.DROP)
                    },
                    clock = clock,
                    telemetryDestination = telemetryDestination,
                    errorHandler = ::trackCaptureError,
                    httpRequestInfoModifierChain = args.httpRequestInfoModifierChain,
                )
            }.onFailure {
                trackCaptureError(it)
            }.getOrNull()
        } else {
            null
        }
    }

    private fun installURLStreamHandlerFactory(
        clock: Clock,
        errorHandler: ErrorHandler,
    ) {
        errorHandler.guardSdkCode(SdkErrorType.UrlStreamHandlerFactoryInstallFail) {
            val httpsHandlerClass = httpsHandlerClassProvider()
            val dataSourceProvider = dataSourceRef::get
            val instrumentedUrlStreamHandlerFactoryProvider = {
                InstrumentedUrlStreamHandlerFactory(
                    httpsHandler = httpsHandlerClass.getDeclaredConstructor().newInstance() as URLStreamHandler,
                    clock = clock,
                    dataSourceProvider = dataSourceProvider,
                )
            }

            val newFactory =
                streamHandlerFactoryFieldProvider()?.let { field ->
                    val existingFactory: Any? = field.get(null)
                    if (existingFactory is URLStreamHandlerFactory) {
                        field.set(null, null)
                        DelegatingInstrumentedURLStreamHandlerFactory(
                            delegateHandlerFactory = existingFactory,
                            instrumentedHandlerFactory = instrumentedUrlStreamHandlerFactoryProvider,
                            clock = clock,
                            dataSourceProvider = dataSourceProvider,
                        )
                    } else {
                        null
                    }
                }

            factoryInstaller(newFactory ?: instrumentedUrlStreamHandlerFactoryProvider())
        }
    }

    private fun trackCaptureError(t: Throwable) {
        errorHandler.trackSdkError(SdkErrorType.DataSourceDataCaptureFail, InstrumentationException(t))
    }

    class RequestData(
        connection: HttpsURLConnection,
        val shouldRecord: (domain: String) -> Boolean,
        val onLimitReached: () -> Unit,
        val clock: Clock,
        val telemetryDestination: TelemetryDestination,
        val errorHandler: (t: Throwable) -> Unit,
        private val httpRequestInfoModifierChain: HttpRequestInfoModifierChain,
    ) {
        private val creationTimeMs = clock.now()
        private val telemetryUrlProvider = {
            getOverriddenURLString(
                request = HucLitePathOverrideRequest(
                    requestHeaderProvider = connection::getRequestProperty,
                    originalUrl = connection.url,
                ),
            )
        }

        private val methodProvider = { connection.requestMethod }
        private val pathProvider = { connection.url.path }
        private val startTimeMs = AtomicLong(INVALID_START_TIME)
        private val requestRecorded = AtomicBoolean(false)

        fun startRequest() =
            runCatching {
                startTimeMs.compareAndSet(INVALID_START_TIME, clock.now())
            }.onFailure {
                errorHandler(it)
            }

        fun completeRequest(responseCode: Int) {
            recordRequest { info ->
                val endTimeMs = clock.now()
                val errorCode = if (responseCode !in 1..<400) {
                    ErrorCodeAttribute.Failure
                } else {
                    null
                }
                val networkRequestSchemaType = SchemaType.NetworkRequest(
                    completedRequestAttributes(
                        url = info.url,
                        httpMethod = info.httpMethod,
                        responseCode = responseCode,
                    ),
                )
                telemetryDestination.recordCompletedSpan(
                    name = "${info.httpMethod} ${pathProvider()}",
                    startTimeMs = getValidStartTime(),
                    endTimeMs = endTimeMs,
                    errorCode = errorCode,
                    type = EmbType.Performance.Network,
                    attributes = networkRequestSchemaType.attributes(),
                )
            }
        }

        fun clientError(t: Throwable) {
            recordRequest { info ->
                val errorTimeMs = clock.now()
                val networkRequestSchemaType = SchemaType.NetworkRequest(
                    incompleteRequestAttributes(
                        url = info.url,
                        httpMethod = info.httpMethod,
                        errorType = t::class.java.canonicalName ?: t::class.java.simpleName,
                        errorMessage = t.message ?: "Unexpected error",
                    ),
                )
                telemetryDestination.recordCompletedSpan(
                    name = "${info.httpMethod} ${pathProvider()}",
                    startTimeMs = getValidStartTime(),
                    endTimeMs = errorTimeMs,
                    errorCode = ErrorCodeAttribute.Failure,
                    type = EmbType.Performance.Network,
                    attributes = networkRequestSchemaType.attributes(),
                )
            }
        }

        private fun completedRequestAttributes(
            url: String,
            httpMethod: String,
            responseCode: Int,
        ): Map<String, String> = buildMap {
            put(UrlAttributes.URL_FULL, url)
            put(HttpAttributes.HTTP_REQUEST_METHOD, httpMethod)
            put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, responseCode.toStatusCodeString())
            put(UserAgentAttributes.USER_AGENT_NAME, HUC_USER_AGENT_NAME)
            put(UserAgentAttributes.USER_AGENT_VERSION, Build.VERSION.SDK_INT.toString())
        }

        private fun incompleteRequestAttributes(
            url: String,
            httpMethod: String,
            errorType: String,
            errorMessage: String,
        ): Map<String, String> = buildMap {
            put(UrlAttributes.URL_FULL, url)
            put(HttpAttributes.HTTP_REQUEST_METHOD, httpMethod)
            put(ErrorAttributes.ERROR_TYPE, errorType)
            put(ExceptionAttributes.EXCEPTION_MESSAGE, errorMessage)
            put(UserAgentAttributes.USER_AGENT_NAME, HUC_USER_AGENT_NAME)
            put(UserAgentAttributes.USER_AGENT_VERSION, Build.VERSION.SDK_INT.toString())
        }

        private fun getValidStartTime(): Long =
            if (startTimeMs.get() == INVALID_START_TIME) {
                creationTimeMs
            } else {
                startTimeMs.get()
            }

        private fun recordRequest(
            recordingFunction: (info: HttpRequestInfo) -> Unit,
        ) {
            runCatching {
                if (requestRecorded.compareAndSet(false, true)) {
                    // Apply any registered modifiers so the reported url/method reflect the
                    // consumer's changes. The underlying HTTP request is not affected.
                    val info = httpRequestInfoModifierChain.apply(
                        MutableHttpRequestInfoImpl(
                            httpMethod = methodProvider(),
                            url = telemetryUrlProvider(),
                        ),
                    )
                    NetworkUtils.getDomain(info.url)?.let { domain ->
                        if (shouldRecord(domain)) {
                            recordingFunction(info)
                        } else {
                            onLimitReached()
                        }
                    }
                }
            }.onFailure {
                errorHandler(it)
            }
        }

        private companion object {
            const val INVALID_START_TIME = -1L
            const val HUC_USER_AGENT_NAME = "HttpURLConnection"
        }
    }

    private class InstrumentationException(
        cause: Throwable,
    ) : RuntimeException("Unexpected error during HUC instrumentation", cause)
}

@SuppressLint("PrivateApi")
private fun defaultHttpsHandlerClass(): Class<*> = Class.forName("com.android.okhttp.HttpsHandler")

private fun defaultFactoryFieldProvider(): Field? {
    val fields = URL::class.java.declaredFields
    for (current in fields) {
        if (Modifier.isStatic(current.modifiers) && current.type == URLStreamHandlerFactory::class.java) {
            current.isAccessible = true
            return current
        }
    }
    return null
}
