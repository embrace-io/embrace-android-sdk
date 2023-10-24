package io.embrace.android.embracesdk.okhttp3;

import static io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.TRACEPARENT_HEADER_NAME;
import static io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.logDebug;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.InternalApi;
import io.embrace.android.embracesdk.internal.ApkToolsConfig;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.http.EmbraceHttpPathOverride;
import io.embrace.android.embracesdk.network.http.HttpMethod;
import io.embrace.android.embracesdk.network.http.NetworkCaptureData;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.RealResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;
import okio.RealBufferedSource;

/**
 * Custom OkHttp3 Interceptor implementation that will log the results of the network call
 * to Embrace.io.
 * <p>
 * This interceptor will only intercept network request and responses from client app.
 * OkHttp3 network interceptors are added almost at the end of stack, they are closer to "Wire"
 * so they are able to see catch "real requests".
 * <p>
 * Network Interceptors
 * - Able to operate on intermediate responses like redirects and retries.
 * - Not invoked for cached responses that short-circuit the network.
 * - Observe the data just as it will be transmitted over the network.
 * - Access to the Connection that carries the request.
 */
@InternalApi
public final class EmbraceOkHttp3NetworkInterceptor implements Interceptor {
    static final String ENCODING_GZIP = "gzip";
    static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";
    static final String CONTENT_ENCODING_HEADER_NAME = "Content-Encoding";
    static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";
    static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream";
    private static final String[] networkCallDataParts = new String[]{
        "Response Headers",
        "Request Headers",
        "Query Parameters",
        "Request Body",
        "Response Body"
    };

    final Embrace embrace;

    public EmbraceOkHttp3NetworkInterceptor() {
        this(Embrace.getInstance());
    }

    EmbraceOkHttp3NetworkInterceptor(Embrace embrace) {
        this.embrace = embrace;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request originalRequest = chain.request();

        if (ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED || !embrace.isStarted()) {
            return chain.proceed(originalRequest);
        }

        long preRequestClockOffset = sdkClockOffset();
        boolean networkSpanForwardingEnabled = embrace.getInternalInterface().isNetworkSpanForwardingEnabled();

        String traceparent = null;
        if (networkSpanForwardingEnabled && originalRequest.header(TRACEPARENT_HEADER_NAME) == null) {
            traceparent = embrace.generateW3cTraceparent();
        }

        final Request request = traceparent == null ?
            originalRequest : originalRequest.newBuilder().header(TRACEPARENT_HEADER_NAME, traceparent).build();

        Response networkResponse = chain.proceed(request);
        long postResponseClockOffset = sdkClockOffset();
        Response.Builder responseBuilder = networkResponse.newBuilder().request(request);

        Long contentLength = null;
        // Try to get the content length from the header
        if (networkResponse.header(CONTENT_LENGTH_HEADER_NAME) != null) {
            try {
                contentLength = Long.parseLong(networkResponse.header(CONTENT_LENGTH_HEADER_NAME));
            } catch (Exception ex) {
                // Ignore
            }
        }

        // If we get the body for a server-sent events stream, then we will wait forever
        String contentType = networkResponse.header(CONTENT_TYPE_HEADER_NAME);

        // Tolerant of a charset specified in header,
        // e.g. Content-Type: text/event-stream;charset=UTF-8
        boolean serverSentEvent = contentType != null &&
            contentType.startsWith(CONTENT_TYPE_EVENT_STREAM);

        if (!serverSentEvent && contentLength == null) {
            try {
                BufferedSource source = networkResponse.body().source();
                source.request(Long.MAX_VALUE);
                contentLength = source.buffer().size();
            } catch (Exception ex) {
                // Ignore
            }
        }

        if (contentLength == null) {
            // Otherwise default to zero
            contentLength = 0L;
        }

        boolean shouldCaptureNetworkData =
            embrace.getInternalInterface().shouldCaptureNetworkBody(request.url().toString(), request.method());

        if (shouldCaptureNetworkData &&
            ENCODING_GZIP.equalsIgnoreCase(networkResponse.header(CONTENT_ENCODING_HEADER_NAME)) &&
            HttpHeaders.promisesBody(networkResponse)) {
            ResponseBody body = networkResponse.body();
            if (body != null) {
                Headers strippedHeaders = networkResponse.headers().newBuilder()
                    .removeAll(CONTENT_ENCODING_HEADER_NAME)
                    .removeAll(CONTENT_LENGTH_HEADER_NAME)
                    .build();
                RealResponseBody realResponseBody =
                    new RealResponseBody(
                        contentType,
                        -1L,
                        new RealBufferedSource(new GzipSource(body.source())
                        )
                    );
                responseBuilder.headers(strippedHeaders);
                responseBuilder.body(realResponseBody);
            }
        }

        Response response = responseBuilder.build();

        NetworkCaptureData networkCaptureData = null;
        if (shouldCaptureNetworkData) {
            networkCaptureData = getNetworkCaptureData(request, response);
        }



        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                EmbraceHttpPathOverride.getURLString(new EmbraceOkHttp3PathOverrideRequest(request)),
                HttpMethod.fromString(request.method()),
                response.sentRequestAtMillis() + preRequestClockOffset,
                response.receivedResponseAtMillis() + postResponseClockOffset,
                request.body() != null ? request.body().contentLength() : 0,
                contentLength,
                response.code(),
                request.header(embrace.getTraceIdHeader()),
                networkSpanForwardingEnabled ? request.header(TRACEPARENT_HEADER_NAME) : null,
                networkCaptureData)
        );

        return response;
    }

    private NetworkCaptureData getNetworkCaptureData(Request request, Response response) {
        Map<String, String> requestHeaders = null;
        String requestQueryParams = null;
        Map<String, String> responseHeaders = null;
        byte[] requestBodyBytes = null;
        byte[] responseBodyBytes = null;
        String dataCaptureErrorMessage = null;
        int partsAcquired = 0;

        try {
            responseHeaders = getProcessedHeaders(response.headers().toMultimap());
            partsAcquired++;
            requestHeaders = getProcessedHeaders(request.headers().toMultimap());
            partsAcquired++;
            requestQueryParams = request.url().query();
            partsAcquired++;
            requestBodyBytes = getRequestBody(request);
            partsAcquired++;
            if (HttpHeaders.promisesBody(response)) {
                final ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    BufferedSource okResponseBodySource = responseBody.source();
                    okResponseBodySource.request(Integer.MAX_VALUE);
                    responseBodyBytes = okResponseBodySource.getBuffer().snapshot().toByteArray();
                }
            }
        } catch (Exception e) {
            final StringBuilder errors = new StringBuilder();
            for (int i = partsAcquired; i < 5; i++) {
                errors.append("'").append(networkCallDataParts[i]).append("'");
                if (i != 4) {
                    errors.append(", ");
                }
            }

            dataCaptureErrorMessage = "There were errors in capturing the following part(s) of the network call: %s" + errors;
            logDebug("Failure during the building of NetworkCaptureData. " + dataCaptureErrorMessage, e);
        }

        return new NetworkCaptureData(
            requestHeaders,
            requestQueryParams,
            requestBodyBytes,
            responseHeaders,
            responseBodyBytes,
            dataCaptureErrorMessage
        );
    }

    private HashMap<String, String> getProcessedHeaders(Map<String, List<String>> properties) {
        HashMap<String, String> headers = new HashMap<>();

        for (Map.Entry<String, List<String>> h :
            properties.entrySet()) {
            StringBuilder builder = new StringBuilder();
            for (String value : h.getValue()) {
                if (value != null) {
                    builder.append(value);
                }
            }
            headers.put(h.getKey(), builder.toString());
        }

        return headers;
    }

    private byte[] getRequestBody(final Request request) {
        try {
            final Request requestCopy = request.newBuilder().build();
            RequestBody requestBody = requestCopy.body();
            if (requestBody != null) {
                final Buffer buffer = new Buffer();
                requestBody.writeTo(buffer);
                return buffer.readByteArray();
            }
        } catch (final IOException e) {
            logDebug("Failed to capture okhttp request body.", e);
        }
        return null;
    }

    /**
     * Get the difference between the SDK clock time and the time System.currentTimeMillis() returns, which is used by OkHttp for
     * determining client-side timestamps.
     */
    private long sdkClockOffset() {
        return embrace.getInternalInterface().getSdkCurrentTime() - System.currentTimeMillis();
    }
}
