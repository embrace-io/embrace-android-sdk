package io.embrace.android.embracesdk.internal.instrumentation

import io.embrace.android.embracesdk.instrumentation.huclite.HucLitePathOverrideRequest
import io.embrace.android.embracesdk.instrumentation.huclite.InstrumentationInitializer
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.network.logging.getOverriddenURLString
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.opentelemetry.kotlin.semconv.ErrorAttributes
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.HttpAttributes
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection

class HucLiteDataSource(
    private val args: InstrumentationInstallArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy
) {
    val telemetryDestination = args.destination

    @Volatile
    var enabled = false

    override fun onDataCaptureEnabled() {
        InstrumentationInitializer().installURLStreamHandlerFactory(
            sdkStarted = { enabled },
            currentTimeMs = args.clock::now,
            hucLiteDataSource = this,
            errorHandler = fun(t: Throwable) {
                args.logger.trackInternalError(InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL, t)
            }
        )
        enabled = true
    }

    override fun onDataCaptureDisabled() {
        enabled = false
    }

    fun createRequestData(
        wrappedConnection: HttpsURLConnection,
        sdkStarted: () -> Boolean,
        currentTimeMs: () -> Long,
        errorHandler: (t: Throwable) -> Unit,
    ): RequestData? =
        runCatching {
            RequestData(
                connection = wrappedConnection,
                sdkStarted = sdkStarted,
                currentTimeMs = currentTimeMs,
                telemetryDestination = telemetryDestination,
                errorHandler = errorHandler
            )
        }.onFailure {
            errorHandler(InstrumentationException(it))
        }.getOrNull()

    class RequestData(
        connection: HttpsURLConnection,
        val sdkStarted: () -> Boolean,
        val currentTimeMs: () -> Long,
        val telemetryDestination: TelemetryDestination,
        val errorHandler: (t: Throwable) -> Unit,
    ) {
        private val creationTimeMs = currentTimeMs()
        private val telemetryUrlProvider = {
            getOverriddenURLString(
                request = HucLitePathOverrideRequest(
                    requestHeaderProvider = connection::getRequestProperty,
                    originalUrl = connection.url
                )
            )
        }
        private val methodProvider = { connection.requestMethod }

        private val pathProvider = { connection.url.path }
        private val startTimeMs = AtomicLong(INVALID_START_TIME)
        private val requestRecorded = AtomicBoolean(false)

        fun startRequest(timestampMs: Long = currentTimeMs()) =
            runCatching {
                startTimeMs.compareAndSet(INVALID_START_TIME, timestampMs)
            }.onFailure {
                errorHandler(InstrumentationException(it))
            }

        fun completeRequest(responseCode: Int) {
            recordRequest {
                val endTimeMs = currentTimeMs()
                val errorCode = if (responseCode !in 1..<400) {
                    "failure"
                } else {
                    null
                }
                val method = methodProvider()
                val networkRequestSchemaType = SchemaType.NetworkRequest(
                    completedRequestAttributes(
                        url = telemetryUrlProvider(),
                        httpMethod = method,
                        responseCode = responseCode
                    )
                )
                telemetryDestination.recordCompletedSpan(
                    name = "$method ${pathProvider()}",
                    startTimeMs = getValidStartTime(),
                    endTimeMs = endTimeMs,
                    errorCode = errorCode,
                    type = EmbType.Performance.Network,
                    attributes = networkRequestSchemaType.attributes()
                )
            }
        }

        fun clientError(t: Throwable) {
            recordRequest {
                val errorTimeMs = currentTimeMs()
                val method = methodProvider()
                val networkRequestSchemaType = SchemaType.NetworkRequest(
                    incompleteRequestAttributes(
                        url = telemetryUrlProvider(),
                        httpMethod = method,
                        errorType = t::class.java.canonicalName ?: t::class.java.simpleName,
                        errorMessage = t.message ?: "Unexpected error"
                    )
                )
                telemetryDestination.recordCompletedSpan(
                    name = "$method ${pathProvider()}",
                    startTimeMs = getValidStartTime(),
                    endTimeMs = errorTimeMs,
                    errorCode = ErrorCodeAttribute.Failure.value,
                    type = EmbType.Performance.Network,
                    attributes = networkRequestSchemaType.attributes()
                )
            }
        }

        private fun completedRequestAttributes(
            url: String,
            httpMethod: String,
            responseCode: Int,
        ): Map<String, String> = mapOf(
            "url.full" to url,
            HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE to responseCode,
        ).toNonNullMap().mapValues { it.value.toString() }

        private fun incompleteRequestAttributes(
            url: String,
            httpMethod: String,
            errorType: String,
            errorMessage: String,
        ): Map<String, String> = mapOf(
            "url.full" to url,
            HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
            ErrorAttributes.ERROR_TYPE to errorType,
            ExceptionAttributes.EXCEPTION_MESSAGE to errorMessage,
        ).toNonNullMap().mapValues { it.value }

        private fun getValidStartTime(): Long =
            if (startTimeMs.get() == INVALID_START_TIME) {
                creationTimeMs
            } else {
                startTimeMs.get()
            }

        private fun recordRequest(recordingFunction: () -> Unit) {
            runCatching {
                if (sdkStarted() && requestRecorded.compareAndSet(false, true)) {
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
