package io.embrace.android.embracesdk.internal.utils

import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.util.regex.Pattern

internal object NetworkUtils {

    private const val TRACE_ID_MAXIMUM_ALLOWED_LENGTH = 64
    private const val DNS_PATTERN =
        "([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,63}[a-zA-Z0-9])?)(\\.[a-zA-Z]{1,63})(\\.[a-zA-Z]{1,2})?$"
    private const val IPV4_PATTERN =
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    private const val IPV6_PATTERN = "(([a-fA-F0-9]{1,4}|):){1,7}([a-fA-F0-9]{1,4}|:)"

    private val IpAddrPattern = Pattern.compile("$IPV4_PATTERN|$IPV6_PATTERN")
    private val DomainPattern = Pattern.compile("$DNS_PATTERN|$IPV4_PATTERN|$IPV6_PATTERN")

    @JvmStatic
    fun getValidTraceId(traceId: String?): String? {
        if (traceId == null) {
            return null
        }

        if (!Charset.forName("US-ASCII").newEncoder().canEncode(traceId)) {
            return null
        }

        return if (traceId.length > TRACE_ID_MAXIMUM_ALLOWED_LENGTH) {
            traceId.substring(0, TRACE_ID_MAXIMUM_ALLOWED_LENGTH)
        } else {
            traceId
        }
    }

    /**
     * Gets the host of a URL.
     *
     * @param originalUrl the URL
     * @return the hostname or IP address
     */
    @JvmStatic
    fun getDomain(originalUrl: String): String? {
        // This is necessary for the "new URL(url)" logic.
        val url = if (!originalUrl.startsWith("http")) "http://$originalUrl" else originalUrl

        val matcher = try {
            DomainPattern.matcher(URL(url).host)
        } catch (ignored: MalformedURLException) {
            DomainPattern.matcher(url)
        }

        return when {
            matcher.find() -> matcher.group(0)
            else -> null
        }
    }

    /**
     * Tests whether a hostname is an IP address
     *
     * @param domain the hostname to test
     * @return true if the domain is an IP address, false otherwise
     */
    @JvmStatic
    fun isIpAddress(domain: String?) =
        if (domain == null) false else IpAddrPattern.matcher(domain).find()

    /**
     * Strips off the query string and hash fragment from a URL.
     *
     * @param url the URL to parse
     * @return the URL with the hash fragment and query string parameters removed
     */
    @JvmStatic
    public fun stripUrl(url: String): String {
        val pathPos: Int = url.lastIndexOf('/')
        val suffix: String = if (pathPos < 0) url else url.substring(pathPos)

        val queryPos = suffix.indexOf('?')
        val fragmentPos = suffix.indexOf('#')

        val queryPosResult = if (queryPos < 0) Int.MAX_VALUE else queryPos
        val fragmentPosResult = if (fragmentPos < 0) Int.MAX_VALUE else fragmentPos

        val terminalPos = queryPosResult.coerceAtMost(fragmentPosResult)

        return url.substring(
            0,
            (if (pathPos < 0) 0 else pathPos) + suffix.length.coerceAtMost(terminalPos)
        )
    }

    @JvmStatic
    fun getUrlPath(url: String?): String? = try {
        URL(url).path
    } catch (exception: Exception) {
        ""
    }
}
