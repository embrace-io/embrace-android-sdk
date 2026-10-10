package io.embrace.android.embracesdk.benchmark.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CaptureProfileTest {

    @Test
    fun `keys are unique`() {
        val keys = CaptureProfile.entries.map(CaptureProfile::key)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `a profile is found by its key`() {
        CaptureProfile.entries.forEach { profile ->
            assertEquals(profile, captureProfileWithKey(profile.key))
        }
    }

    @Test
    fun `no key means app-only`() {
        assertEquals(CaptureProfile.APP_ONLY, captureProfileWithKey(null))
    }

    @Test
    fun `an unknown key fails and names the keys there are`() {
        val error = assertThrows(IllegalStateException::class.java) { captureProfileWithKey("not-a-profile") }
        CaptureProfile.entries.forEach { profile ->
            assertTrue("${profile.key} missing from: ${error.message}", error.message.orEmpty().contains(profile.key))
        }
    }

    @Test
    fun `every profile's config is packaged and captures the benchmark app`() {
        CaptureProfile.entries.forEach { profile ->
            assertTrue(
                "${profile.key} config does not name the benchmark app",
                profile.traceConfig.contains("atrace_apps: \"$BENCHMARK_APP\""),
            )
        }
    }

    @Test
    fun `only with-context captures scheduling`() {
        CaptureProfile.entries.forEach { profile ->
            assertEquals(
                profile.key,
                profile == CaptureProfile.WITH_CONTEXT,
                profile.traceConfig.contains("sched/sched_switch"),
            )
        }
    }

    @Test
    fun `only memory captures the resident set events`() {
        CaptureProfile.entries.forEach { profile ->
            assertEquals(
                profile.key,
                profile == CaptureProfile.MEMORY,
                profile.traceConfig.contains("kmem/rss_stat"),
            )
        }
    }

    private companion object {
        const val BENCHMARK_APP = "io.embrace.android.embracesdk.macrobenchmark.app"
    }
}
