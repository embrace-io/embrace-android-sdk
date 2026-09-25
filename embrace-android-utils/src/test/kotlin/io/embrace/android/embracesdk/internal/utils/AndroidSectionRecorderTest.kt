package io.embrace.android.embracesdk.internal.utils

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTrace

@RunWith(AndroidJUnit4::class)
internal class AndroidSectionRecorderTest {

    @Before
    fun setUp() {
        ShadowTrace.reset()
        ShadowTrace.setEnabled(true)
    }

    @After
    fun tearDown() {
        ShadowTrace.reset()
    }

    @Test
    fun `a counter is recorded under a name prefixed with emb-, truncated as a section name is`() {
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        AndroidSectionRecorder.setCounter(longName, 1)
        assertEquals(
            listOf("emb-sf-bytes-written" to 4096L, prefixedTraceName(longName) to 1L),
            ShadowTrace.getCounters().map { it.name to it.value },
        )
    }

    @Test
    fun `no counter is recorded while nothing is capturing a trace`() {
        ShadowTrace.setEnabled(false)
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        assertTrue(ShadowTrace.getCounters().isEmpty())
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `no counter is recorded below the supported API version`() {
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        assertTrue(ShadowTrace.getCounters().isEmpty())
    }

    private companion object {
        val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
