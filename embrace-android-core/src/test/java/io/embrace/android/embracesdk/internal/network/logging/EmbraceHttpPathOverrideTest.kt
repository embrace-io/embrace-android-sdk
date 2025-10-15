package io.embrace.android.embracesdk.internal.network.logging

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceHttpPathOverrideTest {

    @Test
    fun `check override path validity`() {
        val request: HttpPathOverrideRequest = mockk(relaxed = true)
        every { request.getURLString() } answers { defaultUrl }
        every { request.getOverriddenURL(customPath) } answers { customUrl }
        every { request.getOverriddenURL("/error") } answers { throw RuntimeException() }

        assertEquals(defaultUrl, getOverriddenURLString(request, null))
        assertEquals(defaultUrl, getOverriddenURLString(request, ""))
        assertEquals(defaultUrl, getOverriddenURLString(request, "/a".repeat(1025)))
        assertEquals(defaultUrl, getOverriddenURLString(request, "/屈福特"))
        assertEquals(defaultUrl, getOverriddenURLString(request, "watford"))
        assertEquals(defaultUrl, getOverriddenURLString(request, "/custom#"))
        assertEquals(defaultUrl, getOverriddenURLString(request, ""))
        assertEquals(defaultUrl, getOverriddenURLString(request, "/error"))
        assertEquals(customUrl, getOverriddenURLString(request, customPath))
    }

    companion object {
        private const val defaultUrl = "https://embrace.io/default-path"
        private const val customPath = "/custom-path"
        private const val customUrl = "https://embrace.io$customPath"
    }
}
