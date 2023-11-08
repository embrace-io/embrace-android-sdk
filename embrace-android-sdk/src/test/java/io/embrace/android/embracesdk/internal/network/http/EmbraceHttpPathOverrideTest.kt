package io.embrace.android.embracesdk.internal.network.http

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceHttpPathOverrideTest {

    @Test
    fun `check override path validity`() {
        val request: HttpPathOverrideRequest = mockk(relaxed = true)
        every { request.urlString } answers { defaultUrl }
        every { request.getOverriddenURL(customPath) } answers { customUrl }
        every { request.getOverriddenURL("/error") } answers { throw RuntimeException() }

        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, null))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, ""))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, "/a".repeat(1025)))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, "/屈福特"))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, "watford"))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, "/custom#"))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, ""))
        assertEquals(defaultUrl, EmbraceHttpPathOverride.getURLString(request, "/error"))
        assertEquals(customUrl, EmbraceHttpPathOverride.getURLString(request, customPath))
    }

    companion object {
        private const val defaultUrl = "https://embrace.io/default-path"
        private const val customPath = "/custom-path"
        private const val customUrl = "https://embrace.io$customPath"
    }
}
