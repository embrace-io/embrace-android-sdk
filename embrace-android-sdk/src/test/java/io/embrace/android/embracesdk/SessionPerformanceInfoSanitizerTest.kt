package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakePerformanceInfo
import io.embrace.android.embracesdk.gating.PerformanceInfoSanitizer
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionPerformanceInfoSanitizerTest {

    private val sessionPerformanceInfo = fakePerformanceInfo()

    @Test
    fun `test if it keeps all performance info fields`() {
        // enabled components contains everything about session performance info
        val components = setOf(
            SessionGatingKeys.PERFORMANCE_NETWORK,

            SessionGatingKeys.PERFORMANCE_ANR,
            SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE,
            SessionGatingKeys.PERFORMANCE_CPU,
            SessionGatingKeys.PERFORMANCE_CONNECTIVITY,
            SessionGatingKeys.PERFORMANCE_LOW_MEMORY
        )

        val result = PerformanceInfoSanitizer(sessionPerformanceInfo, components).sanitize()

        assertNotNull(result?.networkRequests)

        assertNotNull(result?.anrIntervals)
        assertNotNull(result?.networkInterfaceIntervals)
        assertNotNull(result?.diskUsage)
    }

    @Test
    fun `test if it sanitizes performance info`() {
        val components = setOf<String>()

        val result = PerformanceInfoSanitizer(sessionPerformanceInfo, components).sanitize()

        assertNull(result?.networkRequests)

        assertNull(result?.anrIntervals)
        assertNull(result?.networkInterfaceIntervals)
        assertNull(result?.memoryWarnings)
        assertNull(result?.diskUsage)
    }
}
