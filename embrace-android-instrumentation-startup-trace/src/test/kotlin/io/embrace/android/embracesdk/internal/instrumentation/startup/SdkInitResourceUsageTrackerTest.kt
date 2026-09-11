package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.NUMERIC_ERROR
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SdkInitResourceUsageTrackerTest {

    private lateinit var cpuTimesMs: ArrayDeque<Long>
    private lateinit var wallTimesMs: ArrayDeque<Long>
    private lateinit var schedstatContents: ArrayDeque<ByteArray?>
    private lateinit var procIoContents: ArrayDeque<ByteArray?>
    private lateinit var runtimeStats: MutableMap<String, ArrayDeque<String?>>
    private lateinit var readPaths: MutableList<String>
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        cpuTimesMs = ArrayDeque(listOf(100L, 130L))
        wallTimesMs = ArrayDeque(listOf(5000L, 5060L))
        // fields are "<running_ns> <run_delay_ns> <timeslices>"
        schedstatContents = ArrayDeque(
            listOf(
                "300000000 5000000 120\n".toByteArray(),
                "800000000 12000000 150\n".toByteArray(),
            ),
        )
        procIoContents = ArrayDeque(
            listOf(
                "rchar: 900\nwchar: 100\nsyscr: 5\nsyscw: 2\nread_bytes: 4096\nwrite_bytes: 0\n".toByteArray(),
                "rchar: 9000\nwchar: 400\nsyscr: 15\nsyscw: 4\nread_bytes: 53248\nwrite_bytes: 0\n".toByteArray(),
            ),
        )
        runtimeStats = mutableMapOf(
            "art.gc.gc-count" to ArrayDeque(listOf("3", "5")),
        )
        readPaths = mutableListOf()
        logger = FakeInternalLogger(throwOnInternalError = false)
    }

    @Test
    fun `window metrics reported from captured deltas`() {
        val attributes = buildTrackerAttributes()
        attributes.assertCpuInitPct()
        attributes.assertInitRunDelayPct()
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertNoInternalErrors()
    }

    @Test
    fun `a build without the gc stat omits attribute`() {
        runtimeStats = mutableMapOf()
        val attributes = buildTrackerAttributes()
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_GC_COUNT)
        attributes.assertCpuInitPct()
        attributes.assertInitRunDelayPct()
        attributes.assertInitDiskRead()
        assertNoInternalErrors()
    }

    @Test
    fun `gc count is omitted below the version that introduced the stat`() {
        runtimeStats = mutableMapOf()
        val tracker = createTracker(versionChecker = { min -> min < INIT_GC_COUNT_MIN_API })
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_GC_COUNT)
        attributes.assertCpuInitPct()
        attributes.assertInitRunDelayPct()
        attributes.assertInitDiskRead()
        assertNoInternalErrors()
    }

    @Test
    fun `a gc count that is not a number is an error`() {
        runtimeStats = mutableMapOf("art.gc.gc-count" to ArrayDeque(listOf("three", "five")))
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_GC_COUNT)
        attributes.assertCpuInitPct()
        attributes.assertInitRunDelayPct()
        attributes.assertInitDiskRead()
        assertCaptureFailureReported(SdkInitAttributeKeys.INIT_GC_COUNT)
    }

    @Test
    fun `relevant attributes omitted if data not found by the proc file reader`() {
        schedstatContents = ArrayDeque(listOf(null, null))
        procIoContents = ArrayDeque(listOf(null, null))
        val attributes = buildTrackerAttributes()
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_DISK_READ_KB)
        attributes.assertCpuInitPct()
        attributes.assertInitGcCount()
        assertNoInternalErrors()
    }

    @Test
    fun `a proc file reader that throws omits the relevant attributes`() {
        val tracker = createTracker(procFileReader = { error("SELinux says no") })
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_DISK_READ_KB)
        attributes.assertCpuInitPct()
        attributes.assertInitGcCount()
        assertNoInternalErrors()
    }

    @Test
    fun `procfs contents we can read but not parse are errors`() {
        schedstatContents = ArrayDeque(listOf("garbage".toByteArray(), "more garbage".toByteArray()))
        procIoContents = ArrayDeque(listOf("rchar: 900\n".toByteArray(), "rchar: 9000\n".toByteArray()))
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_DISK_READ_KB)
        attributes.assertCpuInitPct()
        attributes.assertInitGcCount()
        assertCaptureFailureReported(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT, SdkInitAttributeKeys.INIT_DISK_READ_KB)
    }

    @Test
    fun `schedstat parsing reads exactly the second space-separated field`() {
        schedstatContents = ArrayDeque(
            listOf(
                "100 6000000 999999999\n".toByteArray(),
                "200 18000000 1\n".toByteArray(),
            ),
        )
        val attributes = buildTrackerAttributes()
        attributes.assertAttribute(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT, "20")
        assertNoInternalErrors()
    }

    @Test
    fun `schedstat without a trailing newline still parses`() {
        schedstatContents = ArrayDeque(
            listOf(
                "300000000 5000000 120".toByteArray(),
                "800000000 12000000 150".toByteArray(),
            ),
        )
        val attributes = buildTrackerAttributes()
        attributes.assertInitRunDelayPct()
        assertNoInternalErrors()
    }

    @Test
    fun `schedstat with fewer than two fields is an error`() {
        schedstatContents = ArrayDeque(listOf("12345\n".toByteArray(), "67890\n".toByteArray()))
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertCpuInitPct()
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertCaptureFailureReported(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
    }

    @Test
    fun `schedstat with hours of accumulated run delay parses without overflow`() {
        // cumulative counters on a long-lived device: ~2.5 h of run delay in ns
        schedstatContents = ArrayDeque(
            listOf(
                "1 9000000000000 1\n".toByteArray(),
                "1 9000012000000 1\n".toByteArray(),
            ),
        )
        val attributes = buildTrackerAttributes()
        attributes.assertAttribute(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT, "20")
        assertNoInternalErrors()
    }

    @Test
    fun `attributes based on counter deltas that decreased are errors`() {
        cpuTimesMs = ArrayDeque(listOf(130L, 100L))
        schedstatContents = ArrayDeque(listOf("1 9000000 1".toByteArray(), "1 2000000 1".toByteArray()))
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertCaptureFailureReported(SdkInitAttributeKeys.INIT_CPU_PCT, SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
    }

    @Test
    fun `missing CPU clock time records an error`() {
        cpuTimesMs = ArrayDeque()
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertInitRunDelayPct()
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertCaptureFailureReported(SdkInitAttributeKeys.INIT_CPU_PCT)
    }

    @Test
    fun `failure to start data capture results in the appropriate errors`() {
        val attributes = createTracker().buildAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_DISK_READ_KB)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_GC_COUNT)
        assertCaptureFailureReported(INIT_CPU_SHARES_ATTRS)
    }

    @Test
    fun `failure to end data capture results in the appropriate errors`() {
        val tracker = createTracker()
        tracker.captureStart()
        val attributes = tracker.buildAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_DISK_READ_KB)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_GC_COUNT)
        assertCaptureFailureReported(INIT_CPU_SHARES_ATTRS)
    }

    @Test
    fun `an init faster than the clock can measure results in no init window share attributes`() {
        wallTimesMs = ArrayDeque(listOf(5000L, 5000L))
        val attributes = buildTrackerAttributes()
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertAbsent(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertNoInternalErrors()
    }

    @Test
    fun `a clock that went backwards is an error`() {
        wallTimesMs = ArrayDeque(listOf(5060L, 5000L))
        val attributes = buildTrackerAttributes()
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_CPU_PCT)
        attributes.assertNumberError(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT)
        attributes.assertInitDiskRead()
        attributes.assertInitGcCount()
        assertCaptureFailureReported(INIT_CPU_SHARES_ATTRS)
    }

    private fun buildTrackerAttributes(): Map<String, String> {
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        return attributes
    }

    /**
     * Asserts an attribute capture failure occurred with an optional set of expected attribute names to validate
     */
    private fun assertCaptureFailureReported(vararg keys: String) {
        assertEquals(keys.size, logger.internalErrorMessages.size)
        assertTrue(logger.internalErrorMessages.all { it.msg == "SdkInitAttributeCaptureFail" })
        if (keys.isNotEmpty()) {
            assertEquals(
                keys.toList(),
                logger.internalErrorMessages.map { it.throwable?.message?.substringAfterLast(": ") },
            )
        }
    }

    private fun createTracker(
        versionChecker: VersionChecker = VersionChecker { true },
        procFileReader: (String) -> ByteArray? = { path ->
            readPaths.add(path)
            when {
                path.endsWith("schedstat") -> schedstatContents.removeFirst()
                path.endsWith("/io") -> procIoContents.removeFirst()
                else -> null
            }
        },
    ) = SdkInitResourceUsageTracker(
        logger = logger,
        versionChecker = versionChecker,
        threadCpuTimeMs = { cpuTimesMs.removeFirst() },
        elapsedRealtimeMs = { wallTimesMs.removeFirst() },
        schedstatPathProvider = { SCHEDSTAT_PATH },
        procFileReader = procFileReader,
        runtimeStatReader = { statName -> runtimeStats[statName]?.removeFirstOrNull() },
    )

    private fun Map<String, String>.assertCpuInitPct() = assertAttribute(SdkInitAttributeKeys.INIT_CPU_PCT, "50")

    private fun Map<String, String>.assertInitRunDelayPct() = assertAttribute(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT, "12")

    private fun Map<String, String>.assertInitDiskRead() = assertAttribute(SdkInitAttributeKeys.INIT_DISK_READ_KB, "48")

    private fun Map<String, String>.assertInitGcCount() = assertAttribute(SdkInitAttributeKeys.INIT_GC_COUNT, "2")

    private fun Map<String, String>.assertNumberError(key: String) = assertAttribute(key, NUMERIC_ERROR)

    private fun Map<String, String>.assertAttribute(key: String, value: String) {
        assertEquals(value, this[key])
    }

    private fun Map<String, String>.assertAbsent(key: String) {
        assertFalse(containsKey(key))
    }

    private fun assertNoInternalErrors() {
        assertEquals(0, logger.internalErrorMessages.size)
    }

    private companion object {
        const val SCHEDSTAT_PATH = "/proc/self/task/123/schedstat"

        const val INIT_CPU_SHARES_ATTRS = "init-cpu-shares"
    }
}
