package io.embrace.android.embracesdk.network.http;

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

class EmbraceHttpUrlConnectionImpl<T extends HttpURLConnection> extends HttpURLConnection {

    private final EmbraceHttpUrlConnection embraceConnectionService;

    /**
     * Wraps an existing {@link HttpURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceHttpUrlConnectionImpl(T connection, boolean enableWrapIoStreams) {
        super(connection.getURL());
        embraceConnectionService = new EmbraceUrlConnectionDelegate<>(connection, enableWrapIoStreams);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        embraceConnectionService.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        embraceConnectionService.connect();
    }

    @Override
    public void disconnect() {
        embraceConnectionService.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return embraceConnectionService.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        embraceConnectionService.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return embraceConnectionService.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        embraceConnectionService.setConnectTimeout(timeout);
    }

    @Override
    public Object getContent() throws IOException {
        return embraceConnectionService.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return embraceConnectionService.getContent(classes);
    }

    @Override
    public String getContentEncoding() {
        return embraceConnectionService.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return embraceConnectionService.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return embraceConnectionService.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return embraceConnectionService.getContentType();
    }

    @Override
    public long getDate() {
        return embraceConnectionService.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return embraceConnectionService.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        embraceConnectionService.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return embraceConnectionService.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        embraceConnectionService.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return embraceConnectionService.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        embraceConnectionService.setDoOutput(doOutput);
    }

    @Override
    public InputStream getErrorStream() {
        return embraceConnectionService.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return embraceConnectionService.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
        return embraceConnectionService.getHeaderField(name);
    }

    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return embraceConnectionService.getHeaderFieldDate(name, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return embraceConnectionService.getHeaderFieldInt(name, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return embraceConnectionService.getHeaderFieldKey(n);
    }

    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(String name, long defaultValue) {
        return embraceConnectionService.getHeaderFieldLong(name, defaultValue);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return embraceConnectionService.getHeaderFields();
    }

    @Override
    public long getIfModifiedSince() {
        return embraceConnectionService.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        embraceConnectionService.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return embraceConnectionService.getInputStream();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return embraceConnectionService.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        embraceConnectionService.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return embraceConnectionService.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return embraceConnectionService.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return embraceConnectionService.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return embraceConnectionService.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        embraceConnectionService.setReadTimeout(timeout);
    }

    @Override
    public String getRequestMethod() {
        return embraceConnectionService.getRequestMethod();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        embraceConnectionService.setRequestMethod(method);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return embraceConnectionService.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return embraceConnectionService.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() throws IOException {
        return embraceConnectionService.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return embraceConnectionService.getResponseMessage();
    }

    @Override
    public URL getURL() {
        return embraceConnectionService.getUrl();
    }

    @Override
    public boolean getUseCaches() {
        return embraceConnectionService.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        embraceConnectionService.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        embraceConnectionService.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        embraceConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        embraceConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        embraceConnectionService.setRequestProperty(key, value);
    }

    @Override
    public String toString() {
        return embraceConnectionService.toString();
    }

    @Override
    public boolean usingProxy() {
        return embraceConnectionService.usingProxy();
    }
}
