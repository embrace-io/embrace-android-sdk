package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.network.http.MutableHttpRequestInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class HttpRequestInfoModifierChainTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var chain: HttpRequestInfoModifierChain

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        chain = HttpRequestInfoModifierChain(errorHandler)
    }

    @Test
    fun `no modifiers leaves the info unchanged`() {
        val result = chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        assertEquals("GET", result.httpMethod)
        assertEquals("https://example.com", result.url)
    }

    @Test
    fun `a modifier alters the reported url and method`() {
        chain.add {
            it.httpMethod = "POST"
            it.url = "https://redacted.com"
        }
        val result = chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        assertEquals("POST", result.httpMethod)
        assertEquals("https://redacted.com", result.url)
    }

    @Test
    fun `modifiers are applied in sequence`() {
        chain.add { it.url = it.url + "/a" }
        chain.add { it.url = it.url + "/b" }
        val result = chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        assertEquals("https://example.com/a/b", result.url)
    }

    @Test
    fun `registering the same modifier twice only invokes it once`() {
        var invocations = 0
        val modifier = HttpRequestInfoModifier { invocations++ }
        chain.add(modifier)
        chain.add(modifier)
        chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        assertEquals(1, invocations)
    }

    @Test
    fun `a removed modifier is no longer invoked`() {
        var invocations = 0
        val modifier = HttpRequestInfoModifier { invocations++ }
        chain.add(modifier)
        chain.remove(modifier)
        chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        assertEquals(0, invocations)
    }

    @Test
    fun `a throwing modifier is isolated, logged, and does not prevent other modifiers from running`() {
        var ranAfter = false
        chain.add { error("boom") }
        chain.add {
            it.url = "https://after.com"
            ranAfter = true
        }
        val result = chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))

        assertEquals(true, ranAfter)
        assertEquals("https://after.com", result.url)
        assertEquals(0, errorHandler.sdkErrorMessages.size)
        assertEquals("boom", errorHandler.warningMessages.single().throwable?.message)
    }

    @Test
    fun `a throwing modifier is only logged once across multiple applications`() {
        chain.add { error("boom") }
        repeat(3) {
            chain.apply(MutableHttpRequestInfoImpl(httpMethod = "GET", url = "https://example.com"))
        }
        assertEquals(1, errorHandler.warningMessages.size)
        assertEquals(0, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `MutableHttpRequestInfoImpl exposes the values it was constructed with`() {
        val info: MutableHttpRequestInfo = MutableHttpRequestInfoImpl(httpMethod = "PUT", url = "https://example.com")
        assertEquals("PUT", info.httpMethod)
        assertEquals("https://example.com", info.url)
    }
}
