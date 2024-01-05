package io.embrace.android.embracesdk.internal.network.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.cert.Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

/**
 * {@link EmbraceHttpUrlConnection} plus additional methods for HTTPS network calls
 */
interface EmbraceHttpsUrlConnection extends EmbraceHttpUrlConnection {

    @Nullable
    String getCipherSuite();

    @Nullable
    Certificate[] getLocalCertificates();

    @Nullable
    Certificate[] getServerCertificates() throws SSLPeerUnverifiedException;

    @Nullable
    SSLSocketFactory getSslSocketFactory();

    void setSslSocketFactory(@NonNull SSLSocketFactory factory);

    @Nullable
    HostnameVerifier getHostnameVerifier();

    void setHostnameVerifier(@NonNull HostnameVerifier verifier);
}
