package io.embrace.android.embracesdk.internal.network.http;

import android.annotation.TargetApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

/**
 * Wrapper implementation of HttpsURLConnection that logs network calls to Embrace.
 * <p>
 * Since Java does not support multiple inheritance, much of the logic in this class is duplicated from
 * {@link EmbraceHttpUrlConnectionImpl} in order to ensure that this class inherits directly from
 * {@link HttpsURLConnection}.
 */
class EmbraceHttpsUrlConnectionImpl<T extends HttpsURLConnection> extends HttpsURLConnection {

    private final EmbraceHttpsUrlConnection embraceHttpsUrlConnectionDelegate;

    /**
     * Wraps an existing {@link HttpsURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceHttpsUrlConnectionImpl(T connection, boolean enableWrapIoStreams) {
        this(connection, new EmbraceUrlConnectionDelegate<>(connection, enableWrapIoStreams));
    }

    EmbraceHttpsUrlConnectionImpl(T connection, EmbraceUrlConnectionDelegate<T> delegate) {
        super(connection.getURL());
        embraceHttpsUrlConnectionDelegate = delegate;
    }

    @Override
    public void addRequestProperty(String key, String value) {
        this.embraceHttpsUrlConnectionDelegate.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        this.embraceHttpsUrlConnectionDelegate.connect();
    }

    @Override
    public void disconnect() {
        this.embraceHttpsUrlConnectionDelegate.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return this.embraceHttpsUrlConnectionDelegate.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        this.embraceHttpsUrlConnectionDelegate.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return this.embraceHttpsUrlConnectionDelegate.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.embraceHttpsUrlConnectionDelegate.setConnectTimeout(timeout);
    }

    @Override
    public Object getContent() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getContent(classes);
    }

    @Override
    public String getContentEncoding() {
        return this.embraceHttpsUrlConnectionDelegate.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return this.embraceHttpsUrlConnectionDelegate.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return this.embraceHttpsUrlConnectionDelegate.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return this.embraceHttpsUrlConnectionDelegate.getContentType();
    }

    @Override
    public long getDate() {
        return this.embraceHttpsUrlConnectionDelegate.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return this.embraceHttpsUrlConnectionDelegate.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        this.embraceHttpsUrlConnectionDelegate.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return this.embraceHttpsUrlConnectionDelegate.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        this.embraceHttpsUrlConnectionDelegate.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return this.embraceHttpsUrlConnectionDelegate.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.embraceHttpsUrlConnectionDelegate.setDoOutput(doOutput);
    }

    @Override
    public InputStream getErrorStream() {
        return this.embraceHttpsUrlConnectionDelegate.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderField(name);
    }

    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderFieldDate(name, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderFieldInt(name, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderFieldKey(n);
    }

    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(String name, long defaultValue) {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderFieldLong(name, defaultValue);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return this.embraceHttpsUrlConnectionDelegate.getHeaderFields();
    }

    @Override
    public long getIfModifiedSince() {
        return this.embraceHttpsUrlConnectionDelegate.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        this.embraceHttpsUrlConnectionDelegate.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getInputStream();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return this.embraceHttpsUrlConnectionDelegate.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        this.embraceHttpsUrlConnectionDelegate.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return this.embraceHttpsUrlConnectionDelegate.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return this.embraceHttpsUrlConnectionDelegate.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.embraceHttpsUrlConnectionDelegate.setReadTimeout(timeout);
    }

    @Override
    public String getRequestMethod() {
        return this.embraceHttpsUrlConnectionDelegate.getRequestMethod();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        this.embraceHttpsUrlConnectionDelegate.setRequestMethod(method);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return this.embraceHttpsUrlConnectionDelegate.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return this.embraceHttpsUrlConnectionDelegate.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return this.embraceHttpsUrlConnectionDelegate.getResponseMessage();
    }

    @Override
    public URL getURL() {
        return this.embraceHttpsUrlConnectionDelegate.getUrl();
    }

    @Override
    public boolean getUseCaches() {
        return this.embraceHttpsUrlConnectionDelegate.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        this.embraceHttpsUrlConnectionDelegate.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        this.embraceHttpsUrlConnectionDelegate.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        this.embraceHttpsUrlConnectionDelegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        this.embraceHttpsUrlConnectionDelegate.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        this.embraceHttpsUrlConnectionDelegate.setRequestProperty(key, value);
    }

    @Override
    public String toString() {
        return this.embraceHttpsUrlConnectionDelegate.toString();
    }

    @Override
    public boolean usingProxy() {
        return this.embraceHttpsUrlConnectionDelegate.usingProxy();
    }

    @Override
    public String getCipherSuite() {
        return this.embraceHttpsUrlConnectionDelegate.getCipherSuite();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return this.embraceHttpsUrlConnectionDelegate.getLocalCertificates();
    }

    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        return this.embraceHttpsUrlConnectionDelegate.getServerCertificates();
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return this.embraceHttpsUrlConnectionDelegate.getSslSocketFactory();
    }

    @Override
    public void setSSLSocketFactory(SSLSocketFactory factory) {
        this.embraceHttpsUrlConnectionDelegate.setSslSocketFactory(factory);
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return this.embraceHttpsUrlConnectionDelegate.getHostnameVerifier();
    }

    @Override
    public void setHostnameVerifier(HostnameVerifier verifier) {
        this.embraceHttpsUrlConnectionDelegate.setHostnameVerifier(verifier);
    }

    @Override
    public Principal getLocalPrincipal() {
        return embraceHttpsUrlConnectionDelegate.getLocalPrincipal();
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return embraceHttpsUrlConnectionDelegate.getPeerPrincipal();
    }
}