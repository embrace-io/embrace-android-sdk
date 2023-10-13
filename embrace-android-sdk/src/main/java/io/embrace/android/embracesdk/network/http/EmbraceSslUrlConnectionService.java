package io.embrace.android.embracesdk.network.http;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.cert.Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

/**
 * Additional methods for Https network calls
 */
interface EmbraceSslUrlConnectionService extends EmbraceUrlConnectionService {

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
