package io.embrace.android.embracesdk.internal.utils

import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getDomain
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.isIpAddress
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.stripUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkUtilsTest {

    private val validIpAddresses = arrayOf(
        "1.1.1.1",
        "255.255.255.255",
        "[::1]",
        "[2001::]",
        "[2001:4860:4860]:8888]",
        "[2001:4860:4860:0:0:0:0]"
    )

    private val invalidIpAddresses = arrayOf(
        "google.com",
        "google.co.uk",
        "foo.google.com",
        "bar.foo.google.com",
        "baz.bar.foo.google.com",
        "baz.bar.foo.google.co.uk"
    )

    private val invalidURLs = arrayOf(
        "google",
        "http://google",
        "http://google.",
        "http://.google",
        "http://google-.com",
        "http://30.168.1.255.1",
        "http://192.168.1.256",
        "http://-1.2.3.4",
        "http://3...3",
        "http://127.1"
    )

    private val validURLs = arrayOf(
        arrayOf("http://google.com", "google.com"),
        arrayOf("http://google.co.uk", "google.co.uk"),
        arrayOf("http://google.com/foo", "google.com"),
        arrayOf("http://foo.google.com/foo", "google.com"),
        arrayOf("http://bar.foo.google.com/foo", "google.com"),
        arrayOf("http://baz.bar.foo.google.com/foo", "google.com"),
        arrayOf("http://baz.bar.foo.google.co.uk/foo", "google.co.uk"),
        arrayOf("http://1.1.1.1/foo", "1.1.1.1"),
        arrayOf("http://1.1.1.1:8888/foo", "1.1.1.1"),
        arrayOf("http://[::1]", "::1"),
        arrayOf("http://[2001::]", "2001::"),
        arrayOf("http://[2001:4860:4860]:8888]", "2001:4860:4860"),
        arrayOf("http://[2001:4860:4860:0:0:0:0]", "2001:4860:4860:0:0:0:0")
    )

    private val urlsToStrip = arrayOf(
        arrayOf("http://google.com", "http://google.com"),
        arrayOf("http://foo.google.com/foo", "http://foo.google.com/foo"),
        arrayOf("http://1.1.1.1/foo", "http://1.1.1.1/foo"),
        arrayOf("http://1.1.1.1:8888/foo?param=1", "http://1.1.1.1:8888/foo"),

        arrayOf("http://foo.google.com/foo?color=blue&limit=100", "http://foo.google.com/foo"),
        arrayOf("http://www.example.org/foo.html#bar", "http://www.example.org/foo.html"),
        arrayOf(
            "http://example.com/index.html#:words:some-context-for-a-(search-term)",
            "http://example.com/index.html"
        ),
    )

    @Test
    fun testValidIPs() {
        for (ip in validIpAddresses) {
            assertTrue("$ip should be a valid IP", isIpAddress(ip))
        }
    }

    @Test
    fun testInvalidIps() {
        for (ip in invalidIpAddresses) {
            assertFalse("$ip should not be a valid IP", isIpAddress(ip))
        }
    }

    @Test
    fun testInvalidUrls() {
        for (url in invalidURLs) {
            assertNull(
                "$url should not be a valid domain",
                getDomain(url)
            )
        }
    }

    @Test
    fun testValidUrls() {
        for (pairs in validURLs) {
            val url = pairs[0]
            val expected = pairs[1]
            val domain = getDomain(url)

            assertTrue("$url should contain a domain", domain != null)

            if (domain != null) {
                assertEquals(
                    "Domain for " + url + " should be " + expected + " not " + domain,
                    domain,
                    expected
                )
            }
        }
    }

    @Test
    fun getValidTraceId() {
        val validTraceId = "1-58406520-a006649127e371903a2de979"

        val traceIdMoreThanAllowedLength =
            "34ec0b8ac9d65e91,34ec0b8ac9d65e9134ec0b8ac9d65e91,34ec0b8ac9d65e91"

        val traceIdMoreEqualAllowedLength =
            "34ec0b8ac9d65e91,34ec0b8ac9d65e9134ec0b8ac9d65e91,34ec0b8ac9d65e"

        assertNull(NetworkUtils.getValidTraceId(null))

        assertNull(NetworkUtils.getValidTraceId("\u00B6containUnicode"))

        assertEquals(validTraceId, NetworkUtils.getValidTraceId(validTraceId))

        assertEquals(
            traceIdMoreEqualAllowedLength,
            NetworkUtils.getValidTraceId(traceIdMoreThanAllowedLength)
        )
    }

    @Test
    fun stripUrl() {
        assertEquals("", stripUrl(""))

        for (pairs in urlsToStrip) {
            val url = pairs[0]
            val expected = pairs[1]
            val strippedUrl = stripUrl(url)

            assertEquals(expected, strippedUrl)
        }
    }
}
