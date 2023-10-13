package io.embrace.android.embracesdk.okhttp3;

import static io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.TRACEPARENT_HEADER_NAME;
import static io.embrace.android.embracesdk.internal.utils.ThrowableUtilsKt.causeMessage;
import static io.embrace.android.embracesdk.internal.utils.ThrowableUtilsKt.causeName;

import java.io.IOException;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.InternalApi;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.http.EmbraceHttpPathOverride;
import io.embrace.android.embracesdk.network.http.HttpMethod;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This interceptor will only intercept errors that client app experiences.
 * <p>
 * We used OkHttp3 application interceptor in this case because this interceptor
 * will be added first in the OkHttp3 interceptors stack. This allows us to catch network errors.
 * OkHttp3 network interceptors are added almost at the end of stack, they are closer to "Wire"
 * so they are not able to see network errors.
 * <p>
 * Application interceptors: - Don't need to worry about intermediate responses like
 * redirects and retries. - Are always invoked once, even if the HTTP response is served
 * from the cache. - Observe the application's original intent. Unconcerned with OkHttp-injected
 * headers like If-None-Match. - Permitted to short-circuit and not call
 * Chain.proceed(). - Permitted to retry and make multiple calls to Chain.proceed().
 * <p>
 * We used the EmbraceGraphQLException to capture the custom path added in the intercept
 * chain process for client errors on graphql requests.
 */
@InternalApi
public class EmbraceOkHttp3ApplicationInterceptor implements Interceptor {
    static final String UNKNOWN_EXCEPTION = "Unknown";
    static final String UNKNOWN_MESSAGE = "An error occurred during the execution of this network request";
    final Embrace embrace;

    private final SdkFacade sdkFacade;

    public EmbraceOkHttp3ApplicationInterceptor() {
        this(Embrace.getInstance(), new SdkFacade());
    }

    EmbraceOkHttp3ApplicationInterceptor(Embrace embrace, SdkFacade sdkFacade) {
        this.embrace = embrace;
        this.sdkFacade = sdkFacade;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long startTime = System.currentTimeMillis();
        Request request = chain.request();
        try {
            // we are not interested in response, just proceed
            return chain.proceed(request);
        } catch (EmbraceCustomPathException e) {
            if (embrace.isStarted()) {
                String urlString = EmbraceHttpPathOverride.getURLString(new EmbraceOkHttp3PathOverrideRequest(request), e.getCustomPath());

                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        urlString,
                        HttpMethod.fromString(request.method()),
                        startTime,
                        System.currentTimeMillis(),
                        causeName(e, UNKNOWN_EXCEPTION),
                        causeMessage(e, UNKNOWN_MESSAGE),
                        request.header(embrace.getTraceIdHeader()),
                        sdkFacade.isNetworkSpanForwardingEnabled() ? request.header(TRACEPARENT_HEADER_NAME) : null,
                        null
                    )
                );
            }
            throw e;
        } catch (Exception e) {
            // we are interested in errors.
            if (embrace.isStarted()) {
                String urlString = EmbraceHttpPathOverride.getURLString(new EmbraceOkHttp3PathOverrideRequest(request));
                String errorType = e.getClass().getCanonicalName();
                String errorMessage = e.getMessage();

                embrace.recordNetworkRequest(
                    EmbraceNetworkRequest.fromIncompleteRequest(
                        urlString,
                        HttpMethod.fromString(request.method()),
                        startTime,
                        System.currentTimeMillis(),
                        errorType != null ? errorType : UNKNOWN_EXCEPTION,
                        errorMessage != null ? errorMessage : UNKNOWN_MESSAGE,
                        request.header(embrace.getTraceIdHeader()),
                        sdkFacade.isNetworkSpanForwardingEnabled() ? request.header(TRACEPARENT_HEADER_NAME) : null,
                        null
                    )
                );
            }
            throw e;
        }
    }
}
