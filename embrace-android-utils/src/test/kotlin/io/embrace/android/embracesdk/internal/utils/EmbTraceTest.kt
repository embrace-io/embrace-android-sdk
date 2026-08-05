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
    }

    @After
    fun tearDown() {
        ShadowTrace.reset()
    }

    @Test
    fun `trace returns the value produced by the lambda`() {
        assertEquals(2, EmbTrace.trace("test") { 1 + 1 })
    }

    @Test
    fun `trace records a section prefixed with emb-`() {
        EmbTrace.trace("test 屈福特") { }
        assertEquals("emb-test 屈福特", ShadowTrace.getPreviousSections().single())
    }

    @Test
    fun `trace truncates long section names to 127 characters`() {
        EmbTrace.trace(longName) { }
        val section = ShadowTrace.getPreviousSections().single()
        assertEquals(127, section.length)
        assertTrue(section.startsWith("emb-a"))
    }

    @Test
    fun `trace runs the lambda but records nothing when tracing is disabled`() {
        ShadowTrace.setEnabled(false)
        assertEquals(2, EmbTrace.trace("test") { 1 + 1 })
        assertTrue(ShadowTrace.getPreviousSections().isEmpty())
    }

    @Config(sdk = [VERSION_CODES.Q])
    @Test
    fun `trace records a section on the minimum supported API version`() {
        EmbTrace.trace("test") { }
        assertEquals("emb-test", ShadowTrace.getPreviousSections().single())
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `trace runs the lambda but records nothing below the supported API version`() {
        assertEquals(2, EmbTrace.trace("test") { 1 + 1 })
        assertTrue(ShadowTrace.getPreviousSections().isEmpty())
    }

    private companion object {
        private val longName = "a".repeat(100) + " " + "b".repeat(100)
    }
}
