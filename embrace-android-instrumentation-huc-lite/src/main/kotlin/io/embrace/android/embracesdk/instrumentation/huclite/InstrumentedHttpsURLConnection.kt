package io.embrace.android.embracesdk.instrumentation.huclite

import android.os.Build
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.Permission
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * A wrapper around [HttpsURLConnection] that defers all function calls to the wrapped connection and handles instrumentation as it
 * goes through its lifecycle
 */
internal class InstrumentedHttpsURLConnection(
    private val wrappedConnection: HttpsURLConnection,
) : HttpsURLConnection(wrappedConnection.url) {

    // From HttpsURLConnection
    override fun getCipherSuite(): String? = wrappedConnection.cipherSuite

    override fun getLocalCertificates(): Array<out Certificate?>? = wrappedConnection.localCertificates

    override fun getServerCertificates(): Array<out Certificate?>? = wrappedConnection.serverCertificates

    override fun getHostnameVerifier(): HostnameVerifier? = wrappedConnection.hostnameVerifier

    override fun setHostnameVerifier(verifier: HostnameVerifier?) {
        wrappedConnection.hostnameVerifier = verifier
    }

    override fun getSSLSocketFactory(): SSLSocketFactory? = wrappedConnection.sslSocketFactory

    override fun setSSLSocketFactory(factory: SSLSocketFactory?) {
        wrappedConnection.sslSocketFactory = factory
    }

    override fun getLocalPrincipal(): Principal? = wrappedConnection.localPrincipal

    override fun getPeerPrincipal(): Principal? = wrappedConnection.peerPrincipal

    // HttpURLConnection methods
    override fun connect() {
        wrappedConnection.connect()
    }

    override fun disconnect() {
        wrappedConnection.disconnect()
    }

    override fun usingProxy(): Boolean = wrappedConnection.usingProxy()

    override fun getRequestMethod(): String = wrappedConnection.requestMethod

    override fun setRequestMethod(method: String?) {
        wrappedConnection.requestMethod = method
    }

    override fun getResponseCode(): Int = wrappedConnection.responseCode

    override fun getResponseMessage(): String? = wrappedConnection.responseMessage

    override fun getHeaderField(n: Int): String? = wrappedConnection.getHeaderField(n)

    override fun getHeaderFieldKey(n: Int): String? = wrappedConnection.getHeaderFieldKey(n)

    override fun setFixedLengthStreamingMode(contentLengthLong: Long) {
        wrappedConnection.setFixedLengthStreamingMode(contentLengthLong)
    }

    override fun setFixedLengthStreamingMode(contentLengthInt: Int) {
        wrappedConnection.setFixedLengthStreamingMode(contentLengthInt)
    }

    override fun setChunkedStreamingMode(mode: Int) {
        wrappedConnection.setChunkedStreamingMode(mode)
    }

    override fun getInstanceFollowRedirects(): Boolean = wrappedConnection.instanceFollowRedirects

    override fun setInstanceFollowRedirects(followRedirects: Boolean) {
        wrappedConnection.instanceFollowRedirects = followRedirects
    }

    override fun getErrorStream(): InputStream? = wrappedConnection.errorStream

    override fun getPermission(): Permission? = wrappedConnection.permission

    // URLConnection methods
    override fun addRequestProperty(key: String?, value: String?) {
        wrappedConnection.addRequestProperty(key, value)
    }

    override fun getRequestProperty(key: String?): String? = wrappedConnection.getRequestProperty(key)

    override fun setRequestProperty(key: String?, value: String?) {
        wrappedConnection.setRequestProperty(key, value)
    }

    override fun getRequestProperties(): MutableMap<String, MutableList<String>> = wrappedConnection.requestProperties

    override fun getHeaderField(name: String?): String? = wrappedConnection.getHeaderField(name)

    override fun getHeaderFields(): MutableMap<String, MutableList<String>> = wrappedConnection.headerFields

    override fun getHeaderFieldInt(name: String?, default: Int): Int = wrappedConnection.getHeaderFieldInt(name, default)

    override fun getHeaderFieldLong(name: String?, default: Long): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wrappedConnection.getHeaderFieldLong(name, default)
        } else {
            wrappedConnection.getHeaderFieldInt(name, default.toInt()).toLong()
        }
    }

    override fun getHeaderFieldDate(name: String?, default: Long): Long = wrappedConnection.getHeaderFieldDate(name, default)

    override fun getContentEncoding(): String? = wrappedConnection.contentEncoding

    override fun getContentLength(): Int = wrappedConnection.contentLength

    override fun getContentLengthLong(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wrappedConnection.contentLengthLong
        } else {
            wrappedConnection.contentLength.toLong()
        }
    }

    override fun getContentType(): String? = wrappedConnection.contentType

    override fun getDate(): Long = wrappedConnection.date

    override fun getExpiration(): Long = wrappedConnection.expiration

    override fun getLastModified(): Long = wrappedConnection.lastModified

    override fun getInputStream(): InputStream = wrappedConnection.inputStream

    override fun getOutputStream(): OutputStream = wrappedConnection.outputStream

    override fun getContent(): Any = wrappedConnection.content

    override fun getContent(classes: Array<out Class<*>>?): Any = wrappedConnection.getContent(classes)

    override fun setDoInput(doinput: Boolean) {
        wrappedConnection.doInput = doinput
    }

    override fun getDoInput(): Boolean = wrappedConnection.doInput

    override fun setDoOutput(dooutput: Boolean) {
        wrappedConnection.doOutput = dooutput
    }

    override fun getDoOutput(): Boolean = wrappedConnection.doOutput

    override fun setAllowUserInteraction(allowuserinteraction: Boolean) {
        wrappedConnection.allowUserInteraction = allowuserinteraction
    }

    override fun getAllowUserInteraction(): Boolean = wrappedConnection.allowUserInteraction

    override fun setUseCaches(useCaches: Boolean) {
        wrappedConnection.useCaches = useCaches
    }

    override fun getUseCaches(): Boolean = wrappedConnection.useCaches

    override fun setIfModifiedSince(ifModifiedSince: Long) {
        wrappedConnection.ifModifiedSince = ifModifiedSince
    }

    override fun getIfModifiedSince(): Long = wrappedConnection.ifModifiedSince

    override fun getDefaultUseCaches(): Boolean = wrappedConnection.defaultUseCaches

    override fun setDefaultUseCaches(defaultUseCaches: Boolean) {
        wrappedConnection.defaultUseCaches = defaultUseCaches
    }

    override fun setConnectTimeout(timeout: Int) {
        wrappedConnection.connectTimeout = timeout
    }

    override fun getConnectTimeout(): Int = wrappedConnection.connectTimeout

    override fun setReadTimeout(timeout: Int) {
        wrappedConnection.readTimeout = timeout
    }

    override fun getReadTimeout(): Int = wrappedConnection.readTimeout

    override fun getURL(): URL = wrappedConnection.url

    override fun toString(): String = wrappedConnection.toString()
}
