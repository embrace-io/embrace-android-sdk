package io.embrace.android.embracesdk.fakes

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.Permission
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class FakeHttpsURLConnection(url: URL) : HttpsURLConnection(url) {

    private val invocations = mutableListOf<Pair<String, List<Any?>>>()

    /**
     * The number of times [method] was called. If [args] is empty, calls are counted regardless of arguments.
     */
    fun callCount(method: String, vararg args: Any?): Int = invocations.count { (name, callArgs) ->
        name == method && (args.isEmpty() || callArgs == args.toList())
    }

    var urlProvider: () -> URL = { url }
    var connectAction: () -> Unit = {}
    var responseCodeProvider: () -> Int = { HTTP_OK }
    var outputStreamProvider: () -> OutputStream = { ByteArrayOutputStream() }
    var inputStreamValue: InputStream = ByteArrayInputStream(ByteArray(0))
    var errorStreamValue: InputStream? = null
    var requestMethodValue: String = "GET"
    var responseMessageValue: String? = null
    var headerFieldValue: String? = null
    var headerFieldKeyValue: String? = null
    var headerFieldsValue: Map<String, List<String>> = emptyMap()
    var headerFieldIntValue: Int = 0
    var headerFieldLongValue: Long = 0L
    var headerFieldDateValue: Long = 0L
    var contentValue: Any? = null
    var contentEncodingValue: String? = null
    var contentLengthValue: Int = 0
    var contentLengthLongValue: Long = 0L
    var contentTypeValue: String? = null
    var dateValue: Long = 0L
    var expirationValue: Long = 0L
    var lastModifiedValue: Long = 0L
    var cipherSuiteValue: String? = null
    var localCertificatesValue: Array<Certificate>? = null
    var serverCertificatesValue: Array<Certificate> = emptyArray()
    var hostnameVerifierValue: HostnameVerifier? = null
    var sslSocketFactoryValue: SSLSocketFactory? = null
    var localPrincipalValue: Principal? = null
    var peerPrincipalValue: Principal? = null
    var usingProxyValue: Boolean = false
    var instanceFollowRedirectsValue: Boolean = false
    var permissionValue: Permission? = null
    val requestPropertyValues: MutableMap<String, String> = mutableMapOf()
    var requestPropertiesValue: Map<String, List<String>> = emptyMap()
    var doInputValue: Boolean = false
    var doOutputValue: Boolean = false
    var allowUserInteractionValue: Boolean = false
    var useCachesValue: Boolean = false
    var ifModifiedSinceValue: Long = 0L
    var defaultUseCachesValue: Boolean = false
    var connectTimeoutValue: Int = 0
    var readTimeoutValue: Int = 0

    private fun <T> record(method: String, vararg args: Any?, value: () -> T): T {
        invocations.add(method to args.toList())
        return value()
    }

    override fun getURL(): URL = record("getURL") { urlProvider() }
    override fun connect() = record("connect") { connectAction() }
    override fun disconnect() = record("disconnect") {}
    override fun getResponseCode(): Int = record("getResponseCode") { responseCodeProvider() }
    override fun getOutputStream(): OutputStream = record("getOutputStream") { outputStreamProvider() }
    override fun getInputStream(): InputStream = record("getInputStream") { inputStreamValue }
    override fun getErrorStream(): InputStream? = record("getErrorStream") { errorStreamValue }
    override fun getResponseMessage(): String? = record("getResponseMessage") { responseMessageValue }
    override fun getHeaderField(n: Int): String? = record("getHeaderField", n) { headerFieldValue }
    override fun getHeaderField(name: String?): String? = record("getHeaderField", name) { headerFieldValue }
    override fun getHeaderFieldKey(n: Int): String? = record("getHeaderFieldKey", n) { headerFieldKeyValue }
    override fun getHeaderFields(): Map<String, List<String>> = record("getHeaderFields") { headerFieldsValue }
    override fun getHeaderFieldInt(name: String?, default: Int): Int =
        record("getHeaderFieldInt", name, default) { headerFieldIntValue }

    override fun getHeaderFieldLong(name: String?, default: Long): Long =
        record("getHeaderFieldLong", name, default) { headerFieldLongValue }

    override fun getHeaderFieldDate(name: String?, default: Long): Long =
        record("getHeaderFieldDate", name, default) { headerFieldDateValue }

    override fun getContent(): Any? = record("getContent") { contentValue }
    override fun getContent(classes: Array<out Class<*>>?): Any? = record("getContent", classes) { contentValue }
    override fun getContentEncoding(): String? = record("getContentEncoding") { contentEncodingValue }
    override fun getContentLength(): Int = record("getContentLength") { contentLengthValue }
    override fun getContentLengthLong(): Long = record("getContentLengthLong") { contentLengthLongValue }
    override fun getContentType(): String? = record("getContentType") { contentTypeValue }
    override fun getDate(): Long = record("getDate") { dateValue }
    override fun getExpiration(): Long = record("getExpiration") { expirationValue }
    override fun getLastModified(): Long = record("getLastModified") { lastModifiedValue }
    override fun getCipherSuite(): String? = record("getCipherSuite") { cipherSuiteValue }
    override fun getLocalCertificates(): Array<Certificate>? = record("getLocalCertificates") { localCertificatesValue }
    override fun getServerCertificates(): Array<Certificate> =
        record("getServerCertificates") { serverCertificatesValue }

    override fun getHostnameVerifier(): HostnameVerifier? = record("getHostnameVerifier") { hostnameVerifierValue }
    override fun setHostnameVerifier(v: HostnameVerifier?) =
        record("setHostnameVerifier", v) { hostnameVerifierValue = v }

    override fun getSSLSocketFactory(): SSLSocketFactory? = record("getSSLSocketFactory") { sslSocketFactoryValue }
    override fun setSSLSocketFactory(sf: SSLSocketFactory?) =
        record("setSSLSocketFactory", sf) { sslSocketFactoryValue = sf }

    override fun getLocalPrincipal(): Principal? = record("getLocalPrincipal") { localPrincipalValue }
    override fun getPeerPrincipal(): Principal? = record("getPeerPrincipal") { peerPrincipalValue }
    override fun usingProxy(): Boolean = record("usingProxy") { usingProxyValue }
    override fun getRequestMethod(): String = record("getRequestMethod") { requestMethodValue }
    override fun setRequestMethod(method: String) = record("setRequestMethod", method) { requestMethodValue = method }
    override fun setFixedLengthStreamingMode(contentLength: Long) =
        record("setFixedLengthStreamingMode", contentLength) {}

    override fun setFixedLengthStreamingMode(contentLength: Int) =
        record("setFixedLengthStreamingMode", contentLength) {}

    override fun setChunkedStreamingMode(chunklen: Int) = record("setChunkedStreamingMode", chunklen) {}
    override fun getInstanceFollowRedirects(): Boolean =
        record("getInstanceFollowRedirects") { instanceFollowRedirectsValue }

    override fun setInstanceFollowRedirects(followRedirects: Boolean) =
        record("setInstanceFollowRedirects", followRedirects) { instanceFollowRedirectsValue = followRedirects }

    override fun getPermission(): Permission? = record("getPermission") { permissionValue }
    override fun addRequestProperty(key: String?, value: String?) = record("addRequestProperty", key, value) {}
    override fun getRequestProperty(key: String?): String? =
        record("getRequestProperty", key) { requestPropertyValues[key] }

    override fun setRequestProperty(key: String?, value: String?) = record("setRequestProperty", key, value) {}
    override fun getRequestProperties(): Map<String, List<String>> =
        record("getRequestProperties") { requestPropertiesValue }

    override fun getDoInput(): Boolean = record("getDoInput") { doInputValue }
    override fun setDoInput(doinput: Boolean) = record("setDoInput", doinput) { doInputValue = doinput }
    override fun getDoOutput(): Boolean = record("getDoOutput") { doOutputValue }
    override fun setDoOutput(dooutput: Boolean) = record("setDoOutput", dooutput) { doOutputValue = dooutput }
    override fun getAllowUserInteraction(): Boolean = record("getAllowUserInteraction") { allowUserInteractionValue }
    override fun setAllowUserInteraction(allowuserinteraction: Boolean) =
        record("setAllowUserInteraction", allowuserinteraction) { allowUserInteractionValue = allowuserinteraction }

    override fun getUseCaches(): Boolean = record("getUseCaches") { useCachesValue }
    override fun setUseCaches(usecaches: Boolean) = record("setUseCaches", usecaches) { useCachesValue = usecaches }
    override fun getIfModifiedSince(): Long = record("getIfModifiedSince") { ifModifiedSinceValue }
    override fun setIfModifiedSince(ifmodifiedsince: Long) =
        record("setIfModifiedSince", ifmodifiedsince) { ifModifiedSinceValue = ifmodifiedsince }

    override fun getDefaultUseCaches(): Boolean = record("getDefaultUseCaches") { defaultUseCachesValue }
    override fun setDefaultUseCaches(defaultusecaches: Boolean) =
        record("setDefaultUseCaches", defaultusecaches) { defaultUseCachesValue = defaultusecaches }

    override fun getConnectTimeout(): Int = record("getConnectTimeout") { connectTimeoutValue }
    override fun setConnectTimeout(timeout: Int) =
        record("setConnectTimeout", timeout) { connectTimeoutValue = timeout }

    override fun getReadTimeout(): Int = record("getReadTimeout") { readTimeoutValue }
    override fun setReadTimeout(timeout: Int) = record("setReadTimeout", timeout) { readTimeoutValue = timeout }
}
