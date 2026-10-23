package io.embrace.android.embracesdk.internal.utils

import android.os.Build.VERSION_CODES
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbTraceTest {

    private val traceApi = FakeTraceApi()

    @Before
    fun setUp() {
        EmbTrace.versionChecker = VersionChecker { min -> VERSION_CODES.Q >= min }
        EmbTrace.traceApi = traceApi
        EmbTrace.durationTracker.reset()
    }

    @After
    fun tearDown() {
        EmbTrace.durationTracker.reset()
        EmbTrace.versionChecker = BuildVersionChecker
        EmbTrace.traceApi = AndroidTraceApi
    }

    @Test
    fun `trace returns the value produced by the lambda`() {
        assertEquals(2, EmbTrace.trace("test") { 1 + 1 })
    }

    @Test
    fun `trace allows a val to be assigned inside the section`() {
        val value: Int
        EmbTrace.trace("test") { value = 1 }
        assertEquals(1, value)
    }

    @Test
    fun `trace records a section prefixed with emb-`() {
        EmbTrace.trace(sectionName = "test 屈福特", recordDuration = true) { }
        assertEquals("emb-test 屈福特", traceApi.previousSections.single())
        assertEquals(setOf("test 屈福特"), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `trace truncates long section names to 127 characters`() {
        EmbTrace.trace(sectionName = longName, recordDuration = true) { }
        val section = traceApi.previousSections.single()
        assertEquals(127, section.length)
        assertTrue(section.startsWith("emb-a"))
        assertEquals(setOf(longName), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `trace runs the lambda and records a duration when tracing is disabled`() {
        traceApi.enabled = false
        assertEquals(2, EmbTrace.trace(sectionName = "test", recordDuration = true) { 1 + 1 })
        assertTrue(traceApi.previousSections.isEmpty())
        assertEquals(setOf("test"), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `trace does not record a duration by default`() {
        EmbTrace.trace("test") { }
        assertTrue(EmbTrace.durationTracker.flush().isEmpty())
    }

    @Test
    fun `trace does not record a duration after the tracker is flushed`() {
        EmbTrace.durationTracker.flush()
        EmbTrace.trace(sectionName = "test", recordDuration = true) { }
        assertTrue(EmbTrace.durationTracker.flush().isEmpty())
    }

    @Test
    fun `trace records a section on the minimum supported API version`() {
        EmbTrace.versionChecker = VersionChecker { min -> VERSION_CODES.Q >= min }
        EmbTrace.trace("test") { }
        assertEquals("emb-test", traceApi.previousSections.single())
    }

    @Test
    fun `trace runs the lambda and records a duration below the supported API version`() {
        EmbTrace.versionChecker = VersionChecker { min -> VERSION_CODES.P >= min }
        assertEquals(2, EmbTrace.trace(sectionName = "test", recordDuration = true) { 1 + 1 })
        assertTrue(traceApi.previousSections.isEmpty())
        assertEquals(setOf("test"), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `counter is recorded on the minimum supported API version`() {
        EmbTrace.versionChecker = VersionChecker { min -> VERSION_CODES.Q >= min }
        EmbTrace.counter("test", 1)
        assertEquals(listOf("emb-test" to 1L), traceApi.counters)
    }

    @Test
    fun `counter is not recorded below the supported API version`() {
        EmbTrace.versionChecker = VersionChecker { min -> VERSION_CODES.P >= min }
        EmbTrace.counter("test", 1)
        assertTrue(traceApi.counters.isEmpty())
    }

    private companion object {
        private val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
