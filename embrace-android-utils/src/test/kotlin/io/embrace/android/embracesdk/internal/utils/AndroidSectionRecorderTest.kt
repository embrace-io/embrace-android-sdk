package io.embrace.android.embracesdk.internal.utils

import android.os.Build.VERSION_CODES
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class AndroidSectionRecorderTest {

    private val traceApi = FakeTraceApi()

    @Before
    fun setUp() {
        AndroidSectionRecorder.versionChecker = VersionChecker { min -> VERSION_CODES.Q >= min }
        AndroidSectionRecorder.traceApi = traceApi
    }

    @After
    fun tearDown() {
        AndroidSectionRecorder.versionChecker = BuildVersionChecker
        AndroidSectionRecorder.traceApi = AndroidTraceApi
    }

    @Test
    fun `a counter is recorded under a name prefixed with emb-, truncated as a section name is`() {
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        AndroidSectionRecorder.setCounter(longName, 1)
        assertEquals(
            listOf("emb-sf-bytes-written" to 4096L, prefixedTraceName(longName) to 1L),
            traceApi.counters,
        )
    }

    @Test
    fun `no counter is recorded while nothing is capturing a trace`() {
        traceApi.enabled = false
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        assertTrue(traceApi.counters.isEmpty())
    }

    @Test
    fun `no counter is recorded below the supported API version`() {
        AndroidSectionRecorder.versionChecker = VersionChecker { min -> VERSION_CODES.P >= min }
        AndroidSectionRecorder.setCounter("sf-bytes-written", 4096)
        assertTrue(traceApi.counters.isEmpty())
    }

    private companion object {
        val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
