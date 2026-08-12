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
internal class EmbTraceTest {

    @Before
    fun setUp() {
        ShadowTrace.reset()
        ShadowTrace.setEnabled(true)
        EmbTrace.durationTracker.reset()
    }

    @After
    fun tearDown() {
        ShadowTrace.reset()
        EmbTrace.durationTracker.reset()
    }

    @Test
    fun `trace returns the value produced by the lambda`() {
        assertEquals(2, EmbTrace.trace("test") { 1 + 1 })
    }

    @Test
    fun `trace records a section prefixed with emb-`() {
        EmbTrace.trace(sectionName = "test 屈福特", recordDuration = true) { }
        assertEquals("emb-test 屈福特", ShadowTrace.getPreviousSections().single())
        assertEquals(setOf("test 屈福特"), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `trace truncates long section names to 127 characters`() {
        EmbTrace.trace(sectionName = longName, recordDuration = true) { }
        val section = ShadowTrace.getPreviousSections().single()
        assertEquals(127, section.length)
        assertTrue(section.startsWith("emb-a"))
        assertEquals(setOf(longName), EmbTrace.durationTracker.flush().keys)
    }

    @Test
    fun `trace runs the lambda and records a duration when tracing is disabled`() {
        ShadowTrace.setEnabled(false)
        assertEquals(2, EmbTrace.trace(sectionName = "test", recordDuration = true) { 1 + 1 })
        assertTrue(ShadowTrace.getPreviousSections().isEmpty())
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

    @Config(sdk = [VERSION_CODES.Q])
    @Test
    fun `trace records a section on the minimum supported API version`() {
        EmbTrace.trace("test") { }
        assertEquals("emb-test", ShadowTrace.getPreviousSections().single())
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `trace runs the lambda and records a duration below the supported API version`() {
        assertEquals(2, EmbTrace.trace(sectionName = "test", recordDuration = true) { 1 + 1 })
        assertTrue(ShadowTrace.getPreviousSections().isEmpty())
        assertEquals(setOf("test"), EmbTrace.durationTracker.flush().keys)
    }

    private companion object {
        private val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
