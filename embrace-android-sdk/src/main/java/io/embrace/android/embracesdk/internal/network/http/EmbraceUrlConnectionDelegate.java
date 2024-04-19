package io.embrace.android.embracesdk.internal.network.http;

import static io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.TRACEPARENT_HEADER_NAME;

import android.annotation.TargetApi;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.http.HttpMethod;
import io.embrace.android.embracesdk.utils.exceptions.function.CheckedSupplier;
import kotlin.jvm.functions.Function0;

/**
 * Wraps {@link HttpURLConnection} to log network calls to Embrace. The wrapper also wraps the
 * InputStream to get an accurate count of bytes received if a Content-Length is not provided by
 * the server.
 * <p>
 * The wrapper handles gzip decompression itself and strips the {@code Content-Length} and
 * {@code Content-Encoding} headers. Typically this is handled transparently by
 * {@link HttpURLConnection} but would prevent us from accessing the {@code Content-Length}.
 * <p>
 * Network logging currently does not follow redirects. The duration is logged from initiation of
 * the network call (upon invocation of any method which would initiate the network call), to the
 * retrieval of the response.
 * <p>
 * As network calls are initiated lazily, we log the network call prior to the calling of any
 * wrapped method which would result in the network call actually being executed, and store a
 * flag to prevent duplication of calls.
 */
@InternalApi
class EmbraceUrlConnectionDelegate<T extends HttpURLConnection> implements EmbraceHttpsUrlConnection {

    /**
     * The content encoding HTTP header.
     */
    static final String CONTENT_ENCODING = "Content-Encoding";

    /**
     * The content length HTTP header.
     */
    static final String CONTENT_LENGTH = "Content-Length";

    /**
     * Reference to the wrapped connection.
     */
    private final T connection;

    /**
     * The time at which the connection was created.
     */
    private final long createdTime;

    /**
     * Whether transparent gzip compression is enabled.
     */
    private final boolean enableWrapIoStreams;

    /**
     * Reference to the SDK that is mockable and fakeable in tests
     */
    private final Embrace embrace;

    /**
     * A reference to the output stream wrapped in a counter, so we can determine the bytes sent.
     */
    private volatile CountingOutputStream outputStream;

    /**
     * Whether the network call has already been logged, to prevent duplication.
     */
    private volatile boolean didLogNetworkCall = false;

    /**
     * The time at which the network call was initiated.
     */
    private volatile Long startTime;

    /**
     * The trace id specified for the request
     */
    private volatile String traceId;

    /**
     * The request headers captured from the http connection.
     */
    private volatile HashMap<String, String> requestHeaders;
    /**
     * Indicates if the request throws a IOException
     */
    private volatile Exception inputStreamAccessException;

    private volatile Exception lastConnectionAccessException;

    private final AtomicLong responseSize = new AtomicLong(-1);

    private final AtomicInteger responseCode = new AtomicInteger(0);

    private final AtomicReference<Map<String, List<String>>> headerFields = new AtomicReference<>(null);

    private final AtomicReference<NetworkCaptureData> networkCaptureData = new AtomicReference<>(null);

    @Nullable
    private volatile String traceparent = null;

    private final boolean isSDKStarted;

    /**
     * Wraps an existing {@link HttpURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceUrlConnectionDelegate(@NonNull T connection, boolean enableWrapIoStreams) {
        this(connection, enableWrapIoStreams, Embrace.getInstance());
    }

    EmbraceUrlConnectionDelegate(@NonNull T connection, boolean enableWrapIoStreams,
                                 @NonNull Embrace embrace) {
        this.connection = connection;
        this.enableWrapIoStreams = enableWrapIoStreams;
        this.embrace = embrace;
        this.createdTime = embrace.getInternalInterface().getSdkCurrentTime();
        this.isSDKStarted = embrace.isStarted();
    }

    @Override
    public void addRequestProperty(@NonNull String key, @Nullable String value) {
        this.connection.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        if (isSDKStarted) {
            identifyTraceId();
            try {
                if (embrace.getInternalInterface().isNetworkSpanForwardingEnabled()) {
                    traceparent = connection.getRequestProperty(TRACEPARENT_HEADER_NAME);
                }
            } catch (Exception e) {
                // Ignore traceparent if there was a problem obtaining it
            }
        }
        this.connection.connect();
    }

    @Override
    public void disconnect() {
        // The network call must be logged before we close the transport
        internalLogNetworkCall(createdTime);
        this.connection.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return this.connection.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        this.connection.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return this.connection.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.connection.setConnectTimeout(timeout);
    }

    @Override
    @Nullable
    public Object getContent() throws IOException {
        identifyTraceId();
        return this.connection.getContent();
    }

    @Override
    @Nullable
    public Object getContent(@NonNull Class<?>[] classes) throws IOException {
        identifyTraceId();
        return this.connection.getContent(classes);
    }

    @Override
    @Nullable
    public String getContentEncoding() {
        return shouldUncompressGzip() ? null : this.connection.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return shouldUncompressGzip() ? -1 : this.connection.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return (shouldUncompressGzip() || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) ?
            -1 : this.connection.getContentLengthLong();
    }

    @Override
    @Nullable
    public String getContentType() {
        return this.connection.getContentType();
    }

    @Override
    public long getDate() {
        return this.connection.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return this.connection.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        this.connection.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return this.connection.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        this.connection.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return this.connection.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.connection.setDoOutput(doOutput);
    }

    @Override

    @Nullable
    public InputStream getErrorStream() {
        return getWrappedInputStream(this.connection.getErrorStream());
    }

    private boolean shouldInterceptHeaderRetrieval(@Nullable String key) {
        return shouldUncompressGzip() && key != null && (key.equalsIgnoreCase(CONTENT_ENCODING) || key.equalsIgnoreCase(CONTENT_LENGTH));
    }

    @Override
    @Nullable
    public String getHeaderField(int n) {
        String key = this.connection.getHeaderFieldKey(n);
        return retrieveHeaderField(key,
            null,
            () -> connection.getHeaderField(n)
        );
    }

    @Override
    @Nullable
    public String getHeaderField(@Nullable String name) {
        return retrieveHeaderField(name,
            null,
            () -> connection.getHeaderField(name)
        );
    }

    @Override
    @Nullable
    public String getHeaderFieldKey(int n) {
        String key = this.connection.getHeaderFieldKey(n);
        return retrieveHeaderField(key,
            null,
            () -> key
        );
    }

    @Override
    public long getHeaderFieldDate(@NonNull String name, long defaultValue) {
        Long result = retrieveHeaderField(name,
            defaultValue,
            () -> connection.getHeaderFieldDate(name, defaultValue)
        );

        return result != null ? result : defaultValue;
    }

    @Override
    public int getHeaderFieldInt(@NonNull String name, int defaultValue) {
        Integer result = retrieveHeaderField(name,
            defaultValue,
            () -> connection.getHeaderFieldInt(name, defaultValue)
        );

        return result != null ? result : defaultValue;
    }


    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(@NonNull String name, long defaultValue) {
        Long result = retrieveHeaderField(name,
            defaultValue,
            () -> Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? -1 :
                this.connection.getHeaderFieldLong(name, defaultValue)

        );
        return result != null ? result : defaultValue;
    }

    @Override
    @Nullable
    public Map<String, List<String>> getHeaderFields() {
        cacheNetworkCallData();
        return headerFields.get();
    }


    private <R> R retrieveHeaderField(@Nullable String name,
                                      R defaultValue,
                                      Function0<R> action) {
        if (name == null) {
            return null;
        }
        if (shouldInterceptHeaderRetrieval(name)) {
            // Strip the content encoding and length headers, as we transparently ungzip the content
            return defaultValue;
        }

        R result = action.invoke();
        cacheNetworkCallData();

        return result;
    }

    @Override
    public long getIfModifiedSince() {
        return this.connection.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        this.connection.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    @Nullable
    public InputStream getInputStream() throws IOException {
        try {
            return getWrappedInputStream(this.connection.getInputStream());
        } catch (IOException e) {
            inputStreamAccessException = e;
            throw e;
        }
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return this.connection.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        this.connection.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return this.connection.getLastModified();
    }

    @Override
    @Nullable
    public OutputStream getOutputStream() throws IOException {
        identifyTraceId();
        OutputStream out = connection.getOutputStream();
        if (enableWrapIoStreams && this.outputStream == null && out != null) {
            this.outputStream = new CountingOutputStream(out);
            return this.outputStream;
        }
        return out;
    }

    @Override
    @Nullable
    public Permission getPermission() throws IOException {
        return this.connection.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return this.connection.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.connection.setReadTimeout(timeout);
    }

    @Override
    @NonNull
    public String getRequestMethod() {
        return this.connection.getRequestMethod();
    }

    @Override
    public void setRequestMethod(@NonNull String method) throws ProtocolException {
        this.connection.setRequestMethod(method);
    }

    @Override
    @Nullable
    public Map<String, List<String>> getRequestProperties() {
        return this.connection.getRequestProperties();
    }

    @Override
    @Nullable
    public String getRequestProperty(@NonNull String key) {
        return this.connection.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() {
        identifyTraceId();
        cacheNetworkCallData();
        return responseCode.get();
    }

    @Override
    @Nullable
    public String getResponseMessage() throws IOException {
        identifyTraceId();
        String responseMsg = this.connection.getResponseMessage();
        cacheNetworkCallData();
        return responseMsg;
    }

    @Override
    @Nullable
    public URL getUrl() {
        return this.connection.getURL();
    }

    @Override
    public boolean getUseCaches() {
        return this.connection.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        this.connection.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        this.connection.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        this.connection.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        this.connection.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(@NonNull String key, @Nullable String value) {
        this.connection.setRequestProperty(key, value);

        if (hasNetworkCaptureRules()) {
            this.requestHeaders = getProcessedHeaders(getRequestProperties());
        }
    }

    @Override
    @NonNull
    public String toString() {
        return this.connection.toString();
    }

    @Override
    public boolean usingProxy() {
        return this.connection.usingProxy();
    }

    /**
     * Given a start time and end time (in milliseconds), logs the network call to Embrace.
     * <p>
     * If this delegate has already logged the call it represents, this method is a no-op.
     */
    synchronized void internalLogNetworkCall(long startTime) {
        if (isSDKStarted && !this.didLogNetworkCall) {
            // We are proactive with setting this flag so that we don't get nested calls to log the network call by virtue of
            // extracting the data we need to log the network call.
            this.didLogNetworkCall = true;  // TODO: Wouldn't this mean that the network call might not be logged
            this.startTime = startTime;
            long endTime = embrace.getInternalInterface().getSdkCurrentTime();

            String url = EmbraceHttpPathOverride.getURLString(new EmbraceHttpUrlConnectionOverride(this.connection));

            try {
                long bytesOut = this.outputStream == null ? 0 : Math.max(this.outputStream.getCount(), 0);
                long contentLength = Math.max(0, responseSize.get());

                if (inputStreamAccessException == null && lastConnectionAccessException == null && responseCode.get() != 0) {
                    embrace.getInternalInterface().recordNetworkRequest(
                        EmbraceNetworkRequest.fromCompletedRequest(
                            url,
                            HttpMethod.fromString(getRequestMethod()),
                            startTime,
                            endTime,
                            bytesOut,
                            contentLength,
                            responseCode.get(),
                            traceId,
                            traceparent,
                            networkCaptureData.get()
                        )
                    );
                } else {
                    String exceptionClass = null;
                    String exceptionMessage = null;

                    // Error that happened when trying to obtain the input stream take precedent over connection access errors after that
                    if (inputStreamAccessException != null) {
                        exceptionClass = inputStreamAccessException.getClass().getCanonicalName();
                        exceptionMessage = inputStreamAccessException.getMessage();
                    } else if (lastConnectionAccessException != null) {
                        exceptionClass = lastConnectionAccessException.getClass().getCanonicalName();
                        exceptionMessage = lastConnectionAccessException.getMessage();
                    }

                    String errorType = exceptionClass != null ? exceptionClass : "UnknownState";
                    String errorMessage = exceptionMessage != null ? exceptionMessage : "HTTP response state unknown";

                    embrace.getInternalInterface().recordNetworkRequest(
                        EmbraceNetworkRequest.fromIncompleteRequest(
                            url,
                            HttpMethod.fromString(getRequestMethod()),
                            startTime,
                            endTime,
                            errorType,
                            errorMessage,
                            traceId,
                            traceparent,
                            networkCaptureData.get()
                        )
                    );
                }
            } catch (Exception e) {
                logError(e);
            }
        }
    }

    @Nullable
    private HashMap<String, String> getProcessedHeaders(@Nullable Map<String, List<String>> properties) {
        if (properties == null) {
            return null;
        }

        HashMap<String, String> headers = new HashMap<>();

        for (Map.Entry<String, List<String>> h : properties.entrySet()) {
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

    /**
     * Wraps an input stream with an input stream which counts the number of bytes read, and then
     * updates the network call service with the correct number of bytes read once the stream has
     * reached the end.
     *
     * @param inputStream the input stream to count
     * @return the wrapped input stream
     */
    private CountingInputStreamWithCallback countingInputStream(InputStream inputStream) {
        return new CountingInputStreamWithCallback(
            inputStream,
            hasNetworkCaptureRules(),
            (responseBody) -> {
                cacheNetworkCallData(responseBody);
                internalLogNetworkCall(startTime);
                return null;
            });
    }


    /**
     * We disable the automatic gzip decompression behavior of {@link HttpURLConnection} in the
     * {@link EmbraceHttpUrlStreamHandler} to ensure that we can count the bytes in the response
     * from the server. We decompress the response transparently to the user only if both:
     * <ul>
     * <li>The user did not specify an encoding</li>
     * <li>The server returned a gzipped response</li>
     * </ul>
     * <p>
     * If the user specified an encoding, even if it is gzip, we do not transparently decompress
     * the response. This is to mirror the behavior of {@link HttpURLConnection} whilst providing
     * us access to the content length.
     *
     * @return true if we should decompress the response, false otherwise
     * @see <a href="https://developer.android.com/reference/java/net/HttpURLConnection#performance">Android Docs</a>
     * @see <a href="https://android.googlesource.com/platform/external/okhttp/+/master/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java">Android Source Code</a>
     */
    private boolean shouldUncompressGzip() {
        String contentEncoding = this.connection.getContentEncoding();
        return enableWrapIoStreams &&
            contentEncoding != null &&
            contentEncoding.equalsIgnoreCase("gzip");
    }

    private void identifyTraceId() {
        if (isSDKStarted && traceId == null) {
            try {
                traceId = getRequestProperty(embrace.getTraceIdHeader());
            } catch (Exception e) {
                Embrace.getInstance().getInternalInterface().logWarning(
                    "Failed to retrieve actual trace id header. Current: " + traceId, null, null);
            }
        }
    }

    @Override
    @Nullable
    public String getCipherSuite() {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getCipherSuite();
        }

        return null;
    }

    @Override
    @Nullable
    public Certificate[] getLocalCertificates() {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getLocalCertificates();
        }

        return new Certificate[0];
    }

    @Override
    @Nullable
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getServerCertificates();
        }

        return new Certificate[0];
    }

    @Override
    @Nullable
    public SSLSocketFactory getSslSocketFactory() {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getSSLSocketFactory();
        }

        return null;
    }

    @Override
    public void setSslSocketFactory(@NonNull SSLSocketFactory factory) {
        if (this.connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) this.connection).setSSLSocketFactory(factory);
        }
    }

    @Override
    @Nullable
    public HostnameVerifier getHostnameVerifier() {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getHostnameVerifier();
        }

        return null;
    }

    @Override
    public void setHostnameVerifier(@NonNull HostnameVerifier verifier) {
        if (this.connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) this.connection).setHostnameVerifier(verifier);
        }
    }

    @Override
    @Nullable
    public Principal getLocalPrincipal() {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getLocalPrincipal();
        }

        return null;
    }

    @Override
    @Nullable
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        if (this.connection instanceof HttpsURLConnection) {
            return ((HttpsURLConnection) this.connection).getPeerPrincipal();
        }

        return null;
    }

    @Nullable
    private InputStream getWrappedInputStream(InputStream connectionInputStream) {
        identifyTraceId();
        startTime = embrace.getInternalInterface().getSdkCurrentTime();

        InputStream in = null;
        if (shouldUncompressGzip()) {
            try {
                CheckedSupplier<GZIPInputStream> gzipInputStreamSupplier = () -> new GZIPInputStream(connectionInputStream);
                in = countingInputStream(new BufferedInputStream(gzipInputStreamSupplier.get()));
            } catch (Throwable t) {
                // This handles the case where it's availability is 0, causing the GZIPInputStream instantiation to fail.
            }
        }

        if (in == null) {
            in = enableWrapIoStreams ?
                countingInputStream(new BufferedInputStream(connectionInputStream)) : connectionInputStream;
        }

        cacheAndLogNetworkCall(startTime);

        return in;
    }

    private void cacheAndLogNetworkCall(long startTime) {
        if (!enableWrapIoStreams) {
            cacheNetworkCallData();
            internalLogNetworkCall(startTime);
        }
    }

    private boolean hasNetworkCaptureRules() {
        if (!isSDKStarted || this.connection.getURL() == null) {
            return false;
        }
        String url = this.connection.getURL().toString();
        String method = this.connection.getRequestMethod();

        return embrace.getInternalInterface().shouldCaptureNetworkBody(url, method);
    }

    private void cacheNetworkCallData() {
        if (isSDKStarted) {
            cacheNetworkCallData(null);
        }
    }

    /**
     * Cache values from response at the first point of availability so that we won't try to retrieve these values when the response
     * is not available.
     */
    private void cacheNetworkCallData(@Nullable byte[] responseBody) {
        if (headerFields.get() == null) {
            synchronized (headerFields) {
                if (headerFields.get() == null) {
                    try {
                        final Map<String, List<String>> responseHeaders;
                        if (!enableWrapIoStreams) {
                            responseHeaders = connection.getHeaderFields();
                        } else {
                            responseHeaders = new HashMap<>(connection.getHeaderFields());
                            responseHeaders.remove(CONTENT_ENCODING);
                            responseHeaders.remove(CONTENT_LENGTH);
                        }
                        headerFields.set(responseHeaders);
                    } catch (Exception e) {
                        lastConnectionAccessException = e;
                    }
                }
            }
        }

        if (responseCode.get() == 0) {
            synchronized (responseCode) {
                if (responseCode.get() == 0) {
                    try {
                        responseCode.set(connection.getResponseCode());
                    } catch (Exception e) {
                        lastConnectionAccessException = e;
                    }
                }
            }
        }

        if (responseSize.get() == -1) {
            synchronized (responseSize) {
                // Only try to retrieve the response size if the connection is connected.
                // Doing so when the connection is finished and has disconnected will result in the re-execution of the request
                if (responseSize.get() == -1) {
                    try {
                        responseSize.set(connection.getContentLength());
                    } catch (Exception e) {
                        lastConnectionAccessException = e;
                    }
                }
            }
        }

        if (shouldCaptureNetworkData()) {
            // If we don't have network capture rules, it's unnecessary to save these values
            synchronized (networkCaptureData) {
                if (shouldCaptureNetworkData()) {
                    try {
                        NetworkCaptureData existingData = networkCaptureData.get();
                        if (existingData == null) {
                            Map<String, String> requestHeaders = this.requestHeaders;
                            String requestQueryParams = connection.getURL().getQuery();
                            byte[] requestBody = this.outputStream != null ? this.outputStream.getRequestBody() : null;
                            Map<String, String> responseHeaders = getProcessedHeaders(headerFields.get());

                            networkCaptureData.set(
                                new NetworkCaptureData(
                                    requestHeaders,
                                    requestQueryParams,
                                    requestBody,
                                    responseHeaders,
                                    responseBody,
                                    null
                                )
                            );
                        } else if (responseBody != null) {
                            // Update the response body field in the cached networkCaptureData object if a subsequent call
                            // is update to update the network logging with this data.
                            networkCaptureData.set(
                                new NetworkCaptureData(
                                    existingData.getRequestHeaders(),
                                    existingData.getRequestQueryParams(),
                                    existingData.getCapturedRequestBody(),
                                    existingData.getResponseHeaders(),
                                    responseBody,
                                    null
                                )
                            );
                        }
                    } catch (Exception e) {
                        lastConnectionAccessException = e;
                    }
                }
            }
        }
    }

    private boolean shouldCaptureNetworkData() {
        return (hasNetworkCaptureRules() && (enableWrapIoStreams || inputStreamAccessException != null)) &&
            (networkCaptureData.get() == null || networkCaptureData.get().getCapturedResponseBody() == null);
    }

    private void logError(@NonNull Throwable t) {
        Embrace.getInstance().getInternalInterface().logInternalError(t);
    }
}
