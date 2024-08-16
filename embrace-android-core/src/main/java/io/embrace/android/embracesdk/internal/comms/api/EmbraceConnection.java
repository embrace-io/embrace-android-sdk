package io.embrace.android.embracesdk.internal.comms.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

interface EmbraceConnection {

    boolean isHttps();

    void setRequestMethod(@NonNull String method) throws ProtocolException;

    void setDoOutput(@NonNull Boolean doOutput);

    void setConnectTimeout(@NonNull Integer timeout);

    void setReadTimeout(@NonNull Integer readTimeout);

    void setRequestProperty(@NonNull String key, @Nullable String value);

    @NonNull
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    EmbraceUrl getURL();

    @Nullable
    String getRequestMethod();

    @Nullable
    String getHeaderField(@NonNull String key);

    @Nullable
    Map<String, List<String>> getHeaderFields();

    @Nullable
    OutputStream getOutputStream() throws IOException;

    @Nullable
    InputStream getInputStream() throws IOException;

    @Nullable
    InputStream getErrorStream();

    void connect() throws IOException;

    int getResponseCode() throws IOException;

    @Nullable
    String getResponseMessage() throws IOException;

    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    void setSSLSocketFactory(@Nullable SSLSocketFactory factory);

    @Nullable
    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    SSLSocketFactory getSSLSocketFactory();
}
