package io.embrace.android.embracesdk.network.http;

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

    private final EmbraceHttpsUrlConnection embraceHttpsUrlConnectionService;


    /**
     * Wraps an existing {@link HttpsURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceHttpsUrlConnectionImpl(T connection, boolean enableWrapIoStreams) {
        super(connection.getURL());
        embraceHttpsUrlConnectionService = new EmbraceUrlConnectionDelegate<>(connection, enableWrapIoStreams);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        this.embraceHttpsUrlConnectionService.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        this.embraceHttpsUrlConnectionService.connect();
    }

    @Override
    public void disconnect() {
        this.embraceHttpsUrlConnectionService.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return this.embraceHttpsUrlConnectionService.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        this.embraceHttpsUrlConnectionService.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return this.embraceHttpsUrlConnectionService.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.embraceHttpsUrlConnectionService.setConnectTimeout(timeout);
    }

    @Override
    public Object getContent() throws IOException {
        return this.embraceHttpsUrlConnectionService.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return this.embraceHttpsUrlConnectionService.getContent(classes);
    }

    @Override
    public String getContentEncoding() {
        return this.embraceHttpsUrlConnectionService.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return this.embraceHttpsUrlConnectionService.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return this.embraceHttpsUrlConnectionService.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return this.embraceHttpsUrlConnectionService.getContentType();
    }

    @Override
    public long getDate() {
        return this.embraceHttpsUrlConnectionService.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return this.embraceHttpsUrlConnectionService.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        this.embraceHttpsUrlConnectionService.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return this.embraceHttpsUrlConnectionService.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        this.embraceHttpsUrlConnectionService.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return this.embraceHttpsUrlConnectionService.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.embraceHttpsUrlConnectionService.setDoOutput(doOutput);
    }

    @Override
    public InputStream getErrorStream() {
        return this.embraceHttpsUrlConnectionService.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return this.embraceHttpsUrlConnectionService.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
        return this.embraceHttpsUrlConnectionService.getHeaderField(name);
    }

    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return this.embraceHttpsUrlConnectionService.getHeaderFieldDate(name, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return this.embraceHttpsUrlConnectionService.getHeaderFieldInt(name, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return this.embraceHttpsUrlConnectionService.getHeaderFieldKey(n);
    }

    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(String name, long defaultValue) {
        return this.embraceHttpsUrlConnectionService.getHeaderFieldLong(name, defaultValue);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return this.embraceHttpsUrlConnectionService.getHeaderFields();
    }

    @Override
    public long getIfModifiedSince() {
        return this.embraceHttpsUrlConnectionService.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        this.embraceHttpsUrlConnectionService.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.embraceHttpsUrlConnectionService.getInputStream();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return this.embraceHttpsUrlConnectionService.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        this.embraceHttpsUrlConnectionService.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return this.embraceHttpsUrlConnectionService.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.embraceHttpsUrlConnectionService.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return this.embraceHttpsUrlConnectionService.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return this.embraceHttpsUrlConnectionService.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.embraceHttpsUrlConnectionService.setReadTimeout(timeout);
    }

    @Override
    public String getRequestMethod() {
        return this.embraceHttpsUrlConnectionService.getRequestMethod();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        this.embraceHttpsUrlConnectionService.setRequestMethod(method);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return this.embraceHttpsUrlConnectionService.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return this.embraceHttpsUrlConnectionService.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() throws IOException {
        return this.embraceHttpsUrlConnectionService.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return this.embraceHttpsUrlConnectionService.getResponseMessage();
    }

    @Override
    public URL getURL() {
        return this.embraceHttpsUrlConnectionService.getUrl();
    }

    @Override
    public boolean getUseCaches() {
        return this.embraceHttpsUrlConnectionService.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        this.embraceHttpsUrlConnectionService.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        this.embraceHttpsUrlConnectionService.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        this.embraceHttpsUrlConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        this.embraceHttpsUrlConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        this.embraceHttpsUrlConnectionService.setRequestProperty(key, value);
    }

    @Override
    public String toString() {
        return this.embraceHttpsUrlConnectionService.toString();
    }

    @Override
    public boolean usingProxy() {
        return this.embraceHttpsUrlConnectionService.usingProxy();
    }

    @Override
    public String getCipherSuite() {
        return this.embraceHttpsUrlConnectionService.getCipherSuite();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return this.embraceHttpsUrlConnectionService.getLocalCertificates();
    }

    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        return this.embraceHttpsUrlConnectionService.getServerCertificates();
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return this.embraceHttpsUrlConnectionService.getSslSocketFactory();
    }

    @Override
    public void setSSLSocketFactory(SSLSocketFactory factory) {
        this.embraceHttpsUrlConnectionService.setSslSocketFactory(factory);
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return this.embraceHttpsUrlConnectionService.getHostnameVerifier();
    }

    @Override
    public void setHostnameVerifier(HostnameVerifier verifier) {
        this.embraceHttpsUrlConnectionService.setHostnameVerifier(verifier);
    }


    public Principal getLocalPrincipal() {
        return embraceHttpsUrlConnectionService.getLocalPrincipal();
    }


    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return embraceHttpsUrlConnectionService.getPeerPrincipal();
    }
}