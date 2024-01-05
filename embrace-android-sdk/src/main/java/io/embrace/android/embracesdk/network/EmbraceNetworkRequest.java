package io.embrace.android.embracesdk.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.embrace.android.embracesdk.network.http.HttpMethod;
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData;

/**
 * This class is used to create manually-recorded network requests.
 */
public final class EmbraceNetworkRequest {

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was returned.
     * If no response was returned, use {@link #fromIncompleteRequest(String, HttpMethod, long, long, String, String)}
     * instead.
     *
     * @param url           the URL of the request.
     * @param httpMethod    the HTTP method of the request.
     * @param startTime     the start time of the request.
     * @param endTime       the end time of the request.
     * @param bytesSent     the number of bytes sent.
     * @param bytesReceived the number of bytes received.
     * @param statusCode    the status code of the response.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromCompletedRequest(@NonNull String url,
                                                             @NonNull HttpMethod httpMethod,
                                                             long startTime,
                                                             long endTime,
                                                             long bytesSent,
                                                             long bytesReceived,
                                                             int statusCode
    ) {
        return fromCompletedRequest(url,
            httpMethod,
            startTime,
            endTime,
            bytesSent,
            bytesReceived,
            statusCode,
            null,
            null);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was returned.
     * If no response was returned, use {@link #fromIncompleteRequest(String, HttpMethod, long, long, String, String)}
     * instead.
     *
     * @param url           the URL of the request.
     * @param httpMethod    the HTTP method of the request.
     * @param startTime     the start time of the request.
     * @param endTime       the end time of the request.
     * @param bytesSent     the number of bytes sent.
     * @param bytesReceived the number of bytes received.
     * @param statusCode    the status code of the response.
     * @param traceId       the trace ID of the request, used for distributed tracing.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromCompletedRequest(@NonNull String url,
                                                             @NonNull HttpMethod httpMethod,
                                                             long startTime,
                                                             long endTime,
                                                             long bytesSent,
                                                             long bytesReceived,
                                                             int statusCode,
                                                             @Nullable String traceId
    ) {
        return fromCompletedRequest(url,
            httpMethod,
            startTime,
            endTime,
            bytesSent,
            bytesReceived,
            statusCode,
            traceId,
            null,
            null);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was returned.
     * If no response was returned, use {@link #fromIncompleteRequest(String, HttpMethod, long, long, String, String)}
     * instead.
     *
     * @param url                the URL of the request.
     * @param httpMethod         the HTTP method of the request.
     * @param startTime          the start time of the request.
     * @param endTime            the end time of the request.
     * @param bytesSent          the number of bytes sent.
     * @param bytesReceived      the number of bytes received.
     * @param statusCode         the status code of the response.
     * @param traceId            the trace ID of the request, used for distributed tracing.
     * @param networkCaptureData the network capture data for the request.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromCompletedRequest(@NonNull String url,
                                                             @NonNull HttpMethod httpMethod,
                                                             long startTime,
                                                             long endTime,
                                                             long bytesSent,
                                                             long bytesReceived,
                                                             int statusCode,
                                                             @Nullable String traceId,
                                                             @Nullable NetworkCaptureData networkCaptureData
    ) {
        return fromCompletedRequest(url,
            httpMethod,
            startTime,
            endTime,
            bytesSent,
            bytesReceived,
            statusCode,
            traceId,
            null,
            networkCaptureData);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was returned.
     * If no response was returned, use {@link #fromIncompleteRequest(String, HttpMethod, long, long, String, String, String)}
     * instead.
     *
     * @param url                the URL of the request.
     * @param httpMethod         the HTTP method of the request.
     * @param startTime          the start time of the request.
     * @param endTime            the end time of the request.
     * @param bytesSent          the number of bytes sent.
     * @param bytesReceived      the number of bytes received.
     * @param statusCode         the status code of the response.
     * @param traceId            the trace ID of the request, used for distributed tracing.
     * @param w3cTraceparent     W3C-compliant traceparent representing the network call that is being recorded
     * @param networkCaptureData the network capture data for the request.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromCompletedRequest(@NonNull String url,
                                                             @NonNull HttpMethod httpMethod,
                                                             long startTime,
                                                             long endTime,
                                                             long bytesSent,
                                                             long bytesReceived,
                                                             int statusCode,
                                                             @Nullable String traceId,
                                                             @Nullable String w3cTraceparent,
                                                             @Nullable NetworkCaptureData networkCaptureData
    ) {
        return new EmbraceNetworkRequest(url,
            httpMethod,
            startTime,
            endTime,
            bytesSent,
            bytesReceived,
            statusCode,
            null,
            null,
            traceId,
            w3cTraceparent,
            networkCaptureData);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was not returned.
     * If a response was returned, use {@link #fromCompletedRequest(String, HttpMethod, long, long, long, long, int)}
     * instead.
     *
     * @param url          the URL of the request.
     * @param httpMethod   the HTTP method of the request.
     * @param startTime    the start time of the request.
     * @param endTime      the end time of the request.
     * @param errorType    the error type that occurred.
     * @param errorMessage the error message
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromIncompleteRequest(
        @NonNull String url,
        @NonNull HttpMethod httpMethod,
        long startTime,
        long endTime,
        @NonNull String errorType,
        @NonNull String errorMessage
    ) {
        return fromIncompleteRequest(url,
            httpMethod,
            startTime,
            endTime,
            errorType,
            errorMessage,
            null);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was not returned.
     * If a response was returned, use {@link #fromCompletedRequest(String, HttpMethod, long, long, long, long, int)}
     * instead.
     *
     * @param url          the URL of the request.
     * @param httpMethod   the HTTP method of the request.
     * @param startTime    the start time of the request.
     * @param endTime      the end time of the request.
     * @param errorType    the error type that occurred.
     * @param errorMessage the error message
     * @param traceId      the trace ID of the request, used for distributed tracing.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromIncompleteRequest(
        @NonNull String url,
        @NonNull HttpMethod httpMethod,
        long startTime,
        long endTime,
        @NonNull String errorType,
        @NonNull String errorMessage,
        @Nullable String traceId
    ) {
        return fromIncompleteRequest(url,
            httpMethod,
            startTime,
            endTime,
            errorType,
            errorMessage,
            traceId,
            null,
            null);
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was not returned.
     * If a response was returned, use {@link #fromCompletedRequest(String, HttpMethod, long, long, long, long, int)}
     * instead.
     *
     * @param url                the URL of the request.
     * @param httpMethod         the HTTP method of the request.
     * @param startTime          the start time of the request.
     * @param endTime            the end time of the request.
     * @param errorType          the error type that occurred.
     * @param errorMessage       the error message
     * @param traceId            the trace ID of the request, used for distributed tracing.
     * @param networkCaptureData the network capture data for the request.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromIncompleteRequest(
        @NonNull String url,
        @NonNull HttpMethod httpMethod,
        long startTime,
        long endTime,
        @NonNull String errorType,
        @NonNull String errorMessage,
        @Nullable String traceId,
        @Nullable NetworkCaptureData networkCaptureData
    ) {
        return new EmbraceNetworkRequest(
            url,
            httpMethod,
            startTime,
            endTime,
            null,
            null,
            null,
            errorType,
            errorMessage,
            traceId,
            null,
            networkCaptureData
        );
    }

    /**
     * Construct a new {@link EmbraceNetworkRequest} instance where a HTTP response was not returned.
     * If a response was returned, use {@link #fromCompletedRequest(String, HttpMethod, long, long, long, long, int)}
     * instead.
     *
     * @param url                the URL of the request.
     * @param httpMethod         the HTTP method of the request.
     * @param startTime          the start time of the request.
     * @param endTime            the end time of the request.
     * @param errorType          the error type that occurred.
     * @param errorMessage       the error message
     * @param traceId            the trace ID of the request, used for distributed tracing.
     * @param w3cTraceparent     W3C-compliant traceparent representing the network call that is being recorded
     * @param networkCaptureData the network capture data for the request.
     * @return a new {@link EmbraceNetworkRequest} instance.
     */
    @NonNull
    public static EmbraceNetworkRequest fromIncompleteRequest(
        @NonNull String url,
        @NonNull HttpMethod httpMethod,
        long startTime,
        long endTime,
        @NonNull String errorType,
        @NonNull String errorMessage,
        @Nullable String traceId,
        @Nullable String w3cTraceparent,
        @Nullable NetworkCaptureData networkCaptureData
    ) {
        return new EmbraceNetworkRequest(
            url,
            httpMethod,
            startTime,
            endTime,
            null,
            null,
            null,
            errorType,
            errorMessage,
            traceId,
            w3cTraceparent,
            networkCaptureData
        );
    }

    /**
     * The request's URL. Must start with http:// or https://
     */
    @NonNull
    private final String url;

    /**
     * The request's method. Must be one of the following: GET, PUT, POST, DELETE, PATCH.
     */
    @NonNull
    private final HttpMethod httpMethod;

    /**
     * The time the request started.
     */
    @NonNull
    private final Long startTime;

    /**
     * The time the request ended. Must be greater than the startTime.
     */
    @NonNull
    private final Long endTime;

    /**
     * The number of bytes received.
     */
    @Nullable
    private final Long bytesReceived;

    /**
     * The number of bytes sent.
     */
    @Nullable
    private final Long bytesSent;

    /**
     * The response status of the request. Must be in the range 100 to 599.
     */
    @Nullable
    private final Integer responseCode;

    /**
     * Error object that describes a non-HTTP error, e.g. a connection error.
     */
    @Nullable
    private final Throwable error;

    /**
     * Error type that describes a non-HTTP error, e.g. a connection error.
     */
    @Nullable
    private final String errorType;

    /**
     * Error message that describes a non-HTTP error, e.g. a connection error.
     */
    @Nullable
    private final String errorMessage;

    /**
     * Optional trace ID that can be used to trace a particular request. Max length is 64 characters.
     */
    @Nullable
    private final String traceId;

    /**
     * Optional W3C-compliant traceparent representing the network call that is being recorded
     */
    @Nullable
    private final String w3cTraceparent;

    /**
     * Network capture data for the request.
     */
    @Nullable
    private final NetworkCaptureData networkCaptureData;

    private EmbraceNetworkRequest(@NonNull String url,
                                  @NonNull HttpMethod httpMethod,
                                  @NonNull Long startTime,
                                  @NonNull Long endTime,
                                  @Nullable Long bytesSent,
                                  @Nullable Long bytesReceived,
                                  @Nullable Integer responseCode,
                                  @Nullable String errorType,
                                  @Nullable String errorMessage,
                                  @Nullable String traceId,
                                  @Nullable String w3cTraceparent,
                                  @Nullable NetworkCaptureData networkCaptureData) {
        this.url = url;
        this.httpMethod = httpMethod;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
        this.responseCode = responseCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.error = null;
        this.traceId = traceId;
        this.w3cTraceparent = w3cTraceparent;
        this.networkCaptureData = networkCaptureData;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public String getHttpMethod() {
        return httpMethod != null ? httpMethod.name().toUpperCase() : null;
    }

    @NonNull
    public Long getStartTime() {
        return startTime;
    }

    @NonNull
    public Long getEndTime() {
        return endTime;
    }

    @NonNull
    public Long getBytesIn() {
        return bytesReceived == null ? 0 : bytesReceived;
    }

    @NonNull
    public Long getBytesOut() {
        return bytesSent == null ? 0 : bytesSent;
    }

    @Nullable
    public Integer getResponseCode() {
        return responseCode;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    @Nullable
    public String getTraceId() {
        return traceId;
    }

    @Nullable
    public String getW3cTraceparent() {
        return w3cTraceparent;
    }

    @Nullable
    public Long getBytesReceived() {
        return bytesReceived;
    }

    @Nullable
    public Long getBytesSent() {
        return bytesSent;
    }

    @Nullable
    public NetworkCaptureData getNetworkCaptureData() {
        return networkCaptureData;
    }

    @Nullable
    public String getErrorType() {
        return errorType;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
