package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.network.HttpCallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCallResultHandlerTest {

    private val endpoint = EmbraceEndpoint.PROGUARD

    @Test
    fun `4xx and 5xx failures fail the build`() {
        listOf(400, 500).forEach { code ->
            val exc = assertThrows(IllegalStateException::class.java) {
                handleHttpCallResult(HttpCallResult.Failure(null, code), endpoint, true)
            }
            assertEquals("Embrace HTTP request failed: ${endpoint.url}, status=$code", exc.message)
        }
    }

    @Test
    fun `errors include the exception type and message`() {
        val exc = assertThrows(IllegalStateException::class.java) {
            handleHttpCallResult(HttpCallResult.Error(java.io.IOException("connection reset")), endpoint, true)
        }
        val msg = checkNotNull(exc.message)
        assertTrue(msg, msg.contains("java.io.IOException: connection reset"))
    }

    @Test
    fun `failures do not fail the build when disabled`() {
        handleHttpCallResult(HttpCallResult.Failure(null, 500), endpoint, false)
    }

    @Test
    fun `success does not fail the build`() {
        handleHttpCallResult(HttpCallResult.Success("", 200), endpoint, true)
    }
}
