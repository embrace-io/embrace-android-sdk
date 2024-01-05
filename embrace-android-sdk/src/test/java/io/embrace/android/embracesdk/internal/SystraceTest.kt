package io.embrace.android.embracesdk.internal

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class SystraceTest {

    @Test
    fun `check name is as expected`() {
        var instance: Systrace.Instance? = null
        try {
            instance = checkNotNull(Systrace.start("test 屈福特"))
            assertEquals("emb-test 屈福特", instance.name)
        } finally {
            instance?.let { Systrace.end(it) }
        }
    }

    @Test
    fun `check long name is truncated and does not throw`() {
        var instance: Systrace.Instance? = null
        try {
            instance = checkNotNull(Systrace.start(longName))
            assertEquals(127, instance.name.length)
            assertTrue(instance.name.startsWith("emb-a"))
        } finally {
            instance?.let { Systrace.end(it) }
        }
    }

    @Config(sdk = [VERSION_CODES.Q])
    @Test
    fun `check supported API version does not throw`() {
        recordAndVerifyTrace()
    }

    @Config(sdk = [VERSION_CODES.R])
    @Test
    fun `check unsupported API version does not throw`() {
        recordAndVerifyTrace()
    }

    private fun recordAndVerifyTrace() {
        val returnValue = Systrace.trace("test") {
            1 + 1
        }
        assertEquals(2, returnValue)
    }

    companion object {
        private val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
