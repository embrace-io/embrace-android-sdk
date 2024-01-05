package io.embrace.android.embracesdk.internal.network.http;

import android.annotation.TargetApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Map;

/**
 * An implementation of HttpURLConnection that forwards calls to an {@link EmbraceUrlConnectionDelegate}
 */
class EmbraceHttpUrlConnectionImpl<T extends HttpURLConnection> extends HttpURLConnection {

    private final EmbraceHttpUrlConnection embraceHttpUrlConnectionDelegate;

    /**
     * Wraps an existing {@link HttpURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceHttpUrlConnectionImpl(T connection, boolean enableWrapIoStreams) {
        this(connection, new EmbraceUrlConnectionDelegate<>(connection, enableWrapIoStreams));
    }

    EmbraceHttpUrlConnectionImpl(T connection, EmbraceUrlConnectionDelegate<T> delegate) {
        super(connection.getURL());
        embraceHttpUrlConnectionDelegate = delegate;
    }

    @Override
    public void addRequestProperty(String key, String value) {
        embraceHttpUrlConnectionDelegate.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        embraceHttpUrlConnectionDelegate.connect();
    }

    @Override
    public void disconnect() {
        embraceHttpUrlConnectionDelegate.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return embraceHttpUrlConnectionDelegate.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        embraceHttpUrlConnectionDelegate.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return embraceHttpUrlConnectionDelegate.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        embraceHttpUrlConnectionDelegate.setConnectTimeout(timeout);
    }

    @Override
    public Object getContent() throws IOException {
        return embraceHttpUrlConnectionDelegate.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return embraceHttpUrlConnectionDelegate.getContent(classes);
    }

    @Override
    public String getContentEncoding() {
        return embraceHttpUrlConnectionDelegate.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return embraceHttpUrlConnectionDelegate.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return embraceHttpUrlConnectionDelegate.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return embraceHttpUrlConnectionDelegate.getContentType();
    }

    @Override
    public long getDate() {
        return embraceHttpUrlConnectionDelegate.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return embraceHttpUrlConnectionDelegate.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        embraceHttpUrlConnectionDelegate.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return embraceHttpUrlConnectionDelegate.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        embraceHttpUrlConnectionDelegate.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return embraceHttpUrlConnectionDelegate.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        embraceHttpUrlConnectionDelegate.setDoOutput(doOutput);
    }

    @Override
    public InputStream getErrorStream() {
        return embraceHttpUrlConnectionDelegate.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return embraceHttpUrlConnectionDelegate.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
        return embraceHttpUrlConnectionDelegate.getHeaderField(name);
    }

    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return embraceHttpUrlConnectionDelegate.getHeaderFieldDate(name, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return embraceHttpUrlConnectionDelegate.getHeaderFieldInt(name, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return embraceHttpUrlConnectionDelegate.getHeaderFieldKey(n);
    }

    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(String name, long defaultValue) {
        return embraceHttpUrlConnectionDelegate.getHeaderFieldLong(name, defaultValue);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return embraceHttpUrlConnectionDelegate.getHeaderFields();
    }

    @Override
    public long getIfModifiedSince() {
        return embraceHttpUrlConnectionDelegate.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        embraceHttpUrlConnectionDelegate.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return embraceHttpUrlConnectionDelegate.getInputStream();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return embraceHttpUrlConnectionDelegate.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        embraceHttpUrlConnectionDelegate.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return embraceHttpUrlConnectionDelegate.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return embraceHttpUrlConnectionDelegate.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return embraceHttpUrlConnectionDelegate.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return embraceHttpUrlConnectionDelegate.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        embraceHttpUrlConnectionDelegate.setReadTimeout(timeout);
    }

    @Override
    public String getRequestMethod() {
        return embraceHttpUrlConnectionDelegate.getRequestMethod();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        embraceHttpUrlConnectionDelegate.setRequestMethod(method);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return embraceHttpUrlConnectionDelegate.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return embraceHttpUrlConnectionDelegate.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() throws IOException {
        return embraceHttpUrlConnectionDelegate.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return embraceHttpUrlConnectionDelegate.getResponseMessage();
    }

    @Override
    public URL getURL() {
        return embraceHttpUrlConnectionDelegate.getUrl();
    }

    @Override
    public boolean getUseCaches() {
        return embraceHttpUrlConnectionDelegate.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        embraceHttpUrlConnectionDelegate.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        embraceHttpUrlConnectionDelegate.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        embraceHttpUrlConnectionDelegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        embraceHttpUrlConnectionDelegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        embraceHttpUrlConnectionDelegate.setRequestProperty(key, value);
    }

    @Override
    public String toString() {
        return embraceHttpUrlConnectionDelegate.toString();
    }

    @Override
    public boolean usingProxy() {
        return embraceHttpUrlConnectionDelegate.usingProxy();
    }
}
