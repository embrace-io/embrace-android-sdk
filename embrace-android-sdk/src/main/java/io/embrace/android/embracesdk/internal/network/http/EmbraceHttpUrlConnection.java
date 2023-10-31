package io.embrace.android.embracesdk.internal.network.http;

import android.annotation.TargetApi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * An interface that duplicates the functionality of HttpURLConnection whose implementation can then wrap an actual HttpURLConnection's
 * implementation so calls can be forwarded from this interface's implementation to the wrapped HttpURLConnection implementation.
 * Having this separate interface allows the actual wrappers ({@link EmbraceHttpUrlConnectionImpl}/{@link EmbraceHttpsUrlConnectionImpl})
 * to share an implementation whilst exposing separate interfaces for what they're wrapping.
 */
interface EmbraceHttpUrlConnection {

    void addRequestProperty(@NonNull String key, @Nullable String value);

    void connect() throws IOException;

    void disconnect();

    boolean getAllowUserInteraction();

    void setAllowUserInteraction(boolean allowUserInteraction);

    int getConnectTimeout();

    void setConnectTimeout(int timeout);

    @Nullable
    Object getContent() throws IOException;

    @Nullable
    Object getContent(Class<?>[] classes) throws IOException;

    @Nullable
    String getContentEncoding();

    int getContentLength();

    @TargetApi(24)
    long getContentLengthLong();

    @Nullable
    String getContentType();

    long getDate();

    boolean getDefaultUseCaches();

    void setDefaultUseCaches(boolean defaultUseCaches);

    boolean getDoInput();

    void setDoInput(boolean doInput);

    boolean getDoOutput();

    void setDoOutput(boolean doOutput);

    @Nullable
    InputStream getErrorStream();

    boolean shouldInterceptHeaderRetrieval(@Nullable String key);

    @Nullable
    String getHeaderField(int n);

    @Nullable
    String getHeaderField(@Nullable String name);

    long getHeaderFieldDate(@NonNull String name, long defaultValue);

    int getHeaderFieldInt(@NonNull String name, int defaultValue);

    @Nullable
    String getHeaderFieldKey(int n);

    @TargetApi(24)
    long getHeaderFieldLong(@NonNull String name, long defaultValue);

    @Nullable
    Map<String, List<String>> getHeaderFields();

    long getIfModifiedSince();

    void setIfModifiedSince(long ifModifiedSince);

    @Nullable
    InputStream getInputStream() throws IOException;

    boolean getInstanceFollowRedirects();

    void setInstanceFollowRedirects(boolean followRedirects);

    long getLastModified();

    @Nullable
    OutputStream getOutputStream() throws IOException;

    @Nullable
    Permission getPermission() throws IOException;

    int getReadTimeout();

    void setReadTimeout(int timeout);

    @NonNull
    String getRequestMethod();

    void setRequestMethod(@NonNull String method) throws ProtocolException;

    @Nullable
    Map<String, List<String>> getRequestProperties();

    @Nullable
    String getRequestProperty(@NonNull String key);

    int getResponseCode() throws IOException;

    @Nullable
    String getResponseMessage() throws IOException;

    @Nullable
    URL getUrl();

    boolean getUseCaches();

    void setUseCaches(boolean useCaches);

    void setChunkedStreamingMode(int chunkLen);

    void setFixedLengthStreamingMode(int contentLength);

    void setFixedLengthStreamingMode(long contentLength);

    void setRequestProperty(@NonNull String key, @Nullable String value);

    @NonNull
    String toString();

    boolean usingProxy();

    @Nullable
    Principal getLocalPrincipal();

    @Nullable
    Principal getPeerPrincipal() throws SSLPeerUnverifiedException;
}