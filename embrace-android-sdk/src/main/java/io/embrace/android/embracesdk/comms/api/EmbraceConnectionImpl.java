package io.embrace.android.embracesdk.comms.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

class EmbraceConnectionImpl implements EmbraceConnection {
    private HttpURLConnection httpUrlConnection;
    private EmbraceUrl url;
    private Integer responseCode = null;

    public EmbraceConnectionImpl(HttpURLConnection embraceConnection, EmbraceUrl url) {
        this.httpUrlConnection = embraceConnection;
        this.url = url;
    }


    @Override
    public boolean isHttps() {
        return httpUrlConnection instanceof HttpsURLConnection;
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        httpUrlConnection.setRequestMethod(method);
    }

    @Override
    public void setDoOutput(Boolean doOutput) {
        httpUrlConnection.setDoOutput(doOutput);
    }

    @Override
    public void setConnectTimeout(Integer timeout) {
        httpUrlConnection.setConnectTimeout(timeout);
    }

    @Override
    public void setReadTimeout(Integer readTimeout) {
        httpUrlConnection.setReadTimeout(readTimeout);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        httpUrlConnection.setRequestProperty(key, value);
    }

    @Override
    public EmbraceUrl getURL() {
        return url;
    }

    @Override
    public String getRequestMethod() {
        return httpUrlConnection.getRequestMethod();
    }

    @Override
    public String getHeaderField(String key) {
        return httpUrlConnection.getHeaderField(key);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return httpUrlConnection.getHeaderFields();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return httpUrlConnection.getOutputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return httpUrlConnection.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return httpUrlConnection.getErrorStream();
    }

    @Override
    public void connect() throws IOException {
        httpUrlConnection.connect();
    }

    @Override
    public int getResponseCode() throws IOException {
        if (responseCode == null) {
            return httpUrlConnection.getResponseCode();
        } else {
            return responseCode;
        }
    }

    @Override
    public String getResponseMessage() throws IOException {
        return httpUrlConnection.getResponseMessage();
    }

    @Override
    public void setSSLSocketFactory(SSLSocketFactory factory) {
        ((HttpsURLConnection) httpUrlConnection).setSSLSocketFactory(factory);
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return ((HttpsURLConnection) httpUrlConnection).getSSLSocketFactory();
    }
}
