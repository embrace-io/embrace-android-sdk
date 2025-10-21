package io.embrace.android.embracesdk.internal.network.logging

import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceHttpPathOverrideTest {

    @Test
    fun `check override path validity`() {
        val request: HttpPathOverrideRequest = FakeHttpOverrideRequest(
            urlString = DEFAULT_URL,
            overriddenUrlStringProvider = ::customUrlProvider
        )

        assertEquals(DEFAULT_URL, getOverriddenURLString(request, null))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, ""))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, "/a".repeat(1025)))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, "/屈福特"))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, "watford"))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, "/custom#"))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, ""))
        assertEquals(DEFAULT_URL, getOverriddenURLString(request, "/error"))
        assertEquals(CUSTOM_URL, getOverriddenURLString(request, CUSTOM_PATH))
    }

    companion object {
        private fun customUrlProvider(pathOverride: String): String {
            return when (pathOverride) {
                CUSTOM_PATH -> {
                    CUSTOM_URL
                }

                "/error" -> {
                    throw RuntimeException()
                }

                else -> {
                    DEFAULT_URL
                }
            }
        }
        private const val DEFAULT_URL = "https://embrace.io/default-path"
        private const val CUSTOM_PATH = "/custom-path"
        private const val CUSTOM_URL = "https://embrace.io$CUSTOM_PATH"
    }
}
