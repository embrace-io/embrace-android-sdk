package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.InternalApi
import io.embrace.android.embracesdk.internal.network.http.EmbraceHttpPathOverride
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * This interceptor will only intercept errors that client app experiences.
 *
 *
 * We used OkHttp3 application interceptor in this case because this interceptor
 * will be added first in the OkHttp3 interceptors stack. This allows us to catch network errors.
 * OkHttp3 network interceptors are added almost at the end of stack, they are closer to "Wire"
 * so they are not able to see network errors.
 *
 *
 * Application interceptors: - Don't need to worry about intermediate responses like
 * redirects and retries. - Are always invoked once, even if the HTTP response is served
 * from the cache. - Observe the application's original intent. Unconcerned with OkHttp-injected
 * headers like If-None-Match. - Permitted to short-circuit and not call
 * Chain.proceed(). - Permitted to retry and make multiple calls to Chain.proceed().
 *
 *
 * We used the EmbraceGraphQLException to capture the custom path added in the intercept
 * chain process for client errors on graphql requests.
 */
@InternalApi
public class EmbraceOkHttp3ApplicationInterceptor internal constructor(
    private val embrace: Embrace
) : Interceptor {

    public constructor() : this(Embrace.getInstance())

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val startTime = embrace.internalInterface.getSdkCurrentTime()
        val request: Request = chain.request()
        return try {
            // we are not interested in response, just proceed
            chain.proceed(request)
        } catch (e: EmbraceCustomPathException) {
            if (embrace.isStarted && !embrace.internalInterface.isInternalNetworkCaptureDisabled()) {
                val urlString = EmbraceHttpPathOverride.getURLString(EmbraceOkHttp3PathOverrideRequest(request), e.customPath)
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        urlString,
                        HttpMethod.fromString(request.method),
                        startTime,
                        embrace.internalInterface.getSdkCurrentTime(),
                        causeName(e, UNKNOWN_EXCEPTION),
                        causeMessage(e, UNKNOWN_MESSAGE),
                        request.header(embrace.traceIdHeader),
                        if (embrace.internalInterface.isNetworkSpanForwardingEnabled()) request.header(TRACEPARENT_HEADER_NAME) else null,
                        null
                    )
                )
            }
            throw e
        } catch (e: Exception) {
            // we are interested in errors.
            if (embrace.isStarted && !embrace.internalInterface.isInternalNetworkCaptureDisabled()) {
                val urlString = EmbraceHttpPathOverride.getURLString(EmbraceOkHttp3PathOverrideRequest(request))
                val errorType = e.javaClass.canonicalName
                val errorMessage = e.message
                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        urlString,
                        HttpMethod.fromString(request.method),
                        startTime,
                        embrace.internalInterface.getSdkCurrentTime(),
                        errorType ?: UNKNOWN_EXCEPTION,
                        errorMessage ?: UNKNOWN_MESSAGE,
                        request.header(embrace.traceIdHeader),
                        if (embrace.internalInterface.isNetworkSpanForwardingEnabled()) request.header(TRACEPARENT_HEADER_NAME) else null,
                        null
                    )
                )
            }
            throw e
        }
    }

    internal companion object {
        const val TRACEPARENT_HEADER_NAME = "traceparent"
        const val UNKNOWN_EXCEPTION = "Unknown"
        const val UNKNOWN_MESSAGE = "An error occurred during the execution of this network request"

        /**
         * Return the canonical name of the cause of a [Throwable]. Handles null elements throughout,
         * including the throwable and its cause, in which case [defaultName] is returned
         */
        fun causeName(throwable: Throwable?, defaultName: String = ""): String {
            return throwable?.cause?.javaClass?.canonicalName ?: defaultName
        }

        /**
         * Return the message of the cause of a [Throwable]. Handles null elements throughout,
         * including the throwable and its cause, in which case [defaultMessage] is returned
         */
        fun causeMessage(throwable: Throwable?, defaultMessage: String = ""): String {
            return throwable?.cause?.message ?: defaultMessage
        }
    }
}
