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
 * {@link EmbraceHttpUrlConnection} in order to ensure that this class inherits directly from
 * {@link HttpsURLConnection}.
 */
class EmbraceHttpsUrlConnection<T extends HttpsURLConnection> extends HttpsURLConnection {

    private final EmbraceSslUrlConnectionService embraceSslUrlConnectionService;


    /**
     * Wraps an existing {@link HttpsURLConnection} with the Embrace network logic.
     *
     * @param connection          the connection to wrap
     * @param enableWrapIoStreams true if we should transparently ungzip the response, else false
     */
    public EmbraceHttpsUrlConnection(T connection, boolean enableWrapIoStreams) {
        super(connection.getURL());
        embraceSslUrlConnectionService = new EmbraceUrlConnectionOverride<>(connection, enableWrapIoStreams);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        this.embraceSslUrlConnectionService.addRequestProperty(key, value);
    }

    @Override
    public void connect() throws IOException {
        this.embraceSslUrlConnectionService.connect();
    }

    @Override
    public void disconnect() {
        this.embraceSslUrlConnectionService.disconnect();
    }

    @Override
    public boolean getAllowUserInteraction() {
        return this.embraceSslUrlConnectionService.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowUserInteraction) {
        this.embraceSslUrlConnectionService.setAllowUserInteraction(allowUserInteraction);
    }

    @Override
    public int getConnectTimeout() {
        return this.embraceSslUrlConnectionService.getConnectTimeout();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.embraceSslUrlConnectionService.setConnectTimeout(timeout);
    }

    @Override
    public Object getContent() throws IOException {
        return this.embraceSslUrlConnectionService.getContent();
    }

    @Override
    public Object getContent(Class[] classes) throws IOException {
        return this.embraceSslUrlConnectionService.getContent(classes);
    }

    @Override
    public String getContentEncoding() {
        return this.embraceSslUrlConnectionService.getContentEncoding();
    }

    @Override
    public int getContentLength() {
        return this.embraceSslUrlConnectionService.getContentLength();
    }

    @Override
    @TargetApi(24)
    public long getContentLengthLong() {
        return this.embraceSslUrlConnectionService.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return this.embraceSslUrlConnectionService.getContentType();
    }

    @Override
    public long getDate() {
        return this.embraceSslUrlConnectionService.getDate();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return this.embraceSslUrlConnectionService.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultUseCaches) {
        this.embraceSslUrlConnectionService.setDefaultUseCaches(defaultUseCaches);
    }

    @Override
    public boolean getDoInput() {
        return this.embraceSslUrlConnectionService.getDoInput();
    }

    @Override
    public void setDoInput(boolean doInput) {
        this.embraceSslUrlConnectionService.setDoInput(doInput);
    }

    @Override
    public boolean getDoOutput() {
        return this.embraceSslUrlConnectionService.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.embraceSslUrlConnectionService.setDoOutput(doOutput);
    }

    @Override
    public InputStream getErrorStream() {
        return this.embraceSslUrlConnectionService.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return this.embraceSslUrlConnectionService.getHeaderField(n);
    }

    @Override
    public String getHeaderField(String name) {
        return this.embraceSslUrlConnectionService.getHeaderField(name);
    }

    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return this.embraceSslUrlConnectionService.getHeaderFieldDate(name, defaultValue);
    }

    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return this.embraceSslUrlConnectionService.getHeaderFieldInt(name, defaultValue);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return this.embraceSslUrlConnectionService.getHeaderFieldKey(n);
    }

    @Override
    @TargetApi(24)
    public long getHeaderFieldLong(String name, long defaultValue) {
        return this.embraceSslUrlConnectionService.getHeaderFieldLong(name, defaultValue);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return this.embraceSslUrlConnectionService.getHeaderFields();
    }

    @Override
    public long getIfModifiedSince() {
        return this.embraceSslUrlConnectionService.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifModifiedSince) {
        this.embraceSslUrlConnectionService.setIfModifiedSince(ifModifiedSince);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.embraceSslUrlConnectionService.getInputStream();
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return this.embraceSslUrlConnectionService.getInstanceFollowRedirects();
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        this.embraceSslUrlConnectionService.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public long getLastModified() {
        return this.embraceSslUrlConnectionService.getLastModified();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.embraceSslUrlConnectionService.getOutputStream();
    }

    @Override
    public Permission getPermission() throws IOException {
        return this.embraceSslUrlConnectionService.getPermission();
    }

    @Override
    public int getReadTimeout() {
        return this.embraceSslUrlConnectionService.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.embraceSslUrlConnectionService.setReadTimeout(timeout);
    }

    @Override
    public String getRequestMethod() {
        return this.embraceSslUrlConnectionService.getRequestMethod();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        this.embraceSslUrlConnectionService.setRequestMethod(method);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return this.embraceSslUrlConnectionService.getRequestProperties();
    }

    @Override
    public String getRequestProperty(String key) {
        return this.embraceSslUrlConnectionService.getRequestProperty(key);
    }

    @Override
    public int getResponseCode() throws IOException {
        return this.embraceSslUrlConnectionService.getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return this.embraceSslUrlConnectionService.getResponseMessage();
    }

    @Override
    public URL getURL() {
        return this.embraceSslUrlConnectionService.getUrl();
    }

    @Override
    public boolean getUseCaches() {
        return this.embraceSslUrlConnectionService.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        this.embraceSslUrlConnectionService.setUseCaches(useCaches);
    }

    @Override
    public void setChunkedStreamingMode(int chunkLen) {
        this.embraceSslUrlConnectionService.setChunkedStreamingMode(chunkLen);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        this.embraceSslUrlConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        this.embraceSslUrlConnectionService.setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        this.embraceSslUrlConnectionService.setRequestProperty(key, value);
    }

    @Override
    public String toString() {
        return this.embraceSslUrlConnectionService.toString();
    }

    @Override
    public boolean usingProxy() {
        return this.embraceSslUrlConnectionService.usingProxy();
    }

    @Override
    public String getCipherSuite() {
        return this.embraceSslUrlConnectionService.getCipherSuite();
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return this.embraceSslUrlConnectionService.getLocalCertificates();
    }

    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        return this.embraceSslUrlConnectionService.getServerCertificates();
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return this.embraceSslUrlConnectionService.getSslSocketFactory();
    }

    @Override
    public void setSSLSocketFactory(SSLSocketFactory factory) {
        this.embraceSslUrlConnectionService.setSslSocketFactory(factory);
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return this.embraceSslUrlConnectionService.getHostnameVerifier();
    }

    @Override
    public void setHostnameVerifier(HostnameVerifier verifier) {
        this.embraceSslUrlConnectionService.setHostnameVerifier(verifier);
    }


    public Principal getLocalPrincipal() {
        return embraceSslUrlConnectionService.getLocalPrincipal();
    }


    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return embraceSslUrlConnectionService.getPeerPrincipal();
    }
}