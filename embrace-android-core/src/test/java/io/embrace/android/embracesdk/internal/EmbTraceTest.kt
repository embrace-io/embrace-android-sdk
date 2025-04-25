package io.embrace.android.embracesdk.internal

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class EmbTraceTest {

    @Test
    fun `check name is as expected`() {
        var instance: EmbTrace.Instance? = null
        try {
            instance = checkNotNull(EmbTrace.startAsync("test 屈福特"))
            assertEquals("emb-test 屈福特", instance.name)
        } finally {
            instance?.let { EmbTrace.endAsync(it) }
        }
    }

    @Test
    fun `check long name is truncated and does not throw`() {
        var instance: EmbTrace.Instance? = null
        try {
            instance = checkNotNull(EmbTrace.startAsync(longName))
            assertEquals(127, instance.name.length)
            assertTrue(instance.name.startsWith("emb-a"))
        } finally {
            instance?.let { EmbTrace.endAsync(it) }
        }
    }

    @Test
    fun `check long name for synchronous trace does not throw`() {
        try {
            EmbTrace.start(longName)
        } finally {
            EmbTrace.end()
        }
    }

    @Config(sdk = [VERSION_CODES.Q])
    @Test
    fun `check supported API version does not throw`() {
        recordAndVerifyAsyncTrace()
        recordAndVerifySynchronousTrace()
    }

    @Config(sdk = [VERSION_CODES.R])
    @Test
    fun `check unsupported API version does not throw`() {
        recordAndVerifyAsyncTrace()
        recordAndVerifySynchronousTrace()
    }

    private fun recordAndVerifyAsyncTrace() {
        val returnValue = EmbTrace.traceAsync("test") {
            1 + 1
        }
        assertEquals(2, returnValue)
    }

    private fun recordAndVerifySynchronousTrace() {
        val returnValue = EmbTrace.trace("test") {
            1 + 1
        }
        assertEquals(2, returnValue)
    }

    companion object {
        private val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
