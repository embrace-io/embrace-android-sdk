package io.embrace.android.embracesdk.internal.instrumentation.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

internal class SdkInitResourceUsageTrackerTest {

    private lateinit var cpuTimesMs: ArrayDeque<Long>
    private lateinit var wallTimesMs: ArrayDeque<Long>
    private lateinit var schedstatContents: ArrayDeque<ByteArray?>
    private lateinit var procIoContents: ArrayDeque<ByteArray?>
    private lateinit var runtimeStats: MutableMap<String, ArrayDeque<String?>>
    private lateinit var readPaths: MutableList<String>

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
    }

    @Test
    fun `window metrics reported from captured deltas`() {
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        // window = 60 ms wall; cpu delta = 30 ms -> 50%; run delay = 7 ms -> 12%;
        // read_bytes delta 49152 B -> 48 KB; gc 5-3 = 2 taking 40-12 = 28 ms
        assertEquals(
            mapOf(
                SdkInitAttributeKeys.INIT_CPU_PCT to "50",
                SdkInitAttributeKeys.INIT_RUN_DELAY_PCT to "12",
                SdkInitAttributeKeys.INIT_DISK_READ_KB to "48",
                SdkInitAttributeKeys.INIT_GC_COUNT to "2",
            ),
            tracker.buildAttributes(),
        )
        assertEquals(listOf(SCHEDSTAT_PATH, SCHEDSTAT_PATH), readPaths.filter { it.endsWith("schedstat") })
    }

    @Test
    fun `missing runtime stats only drops GC count`() {
        runtimeStats = mutableMapOf()
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_GC_COUNT))
        assertEquals("50", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
    }

    @Test
    fun `unreadable schedstat omits run delay but keeps cpu time`() {
        schedstatContents = ArrayDeque(listOf(null, null))
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT))
        assertEquals("50", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
    }

    @Test
    fun `malformed schedstat omits run delay but keeps cpu time`() {
        schedstatContents = ArrayDeque(listOf("garbage".toByteArray(), "more garbage".toByteArray()))
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT))
        assertEquals("50", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
    }

    @Test
    fun `throwing reader omits run delay but keeps cpu time`() {
        val tracker = createTracker(procFileReader = { error("SELinux says no") })
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT))
        assertEquals("50", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
    }

    @Test
    fun `schedstat parsing reads exactly the second space-separated field`() {
        schedstatContents = ArrayDeque(
            listOf(
                "100 6000000 999999999\n".toByteArray(),
                "200 18000000 1\n".toByteArray(),
            ),
        )
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        assertEquals("20", tracker.buildAttributes()[SdkInitAttributeKeys.INIT_RUN_DELAY_PCT])
    }

    @Test
    fun `schedstat without a trailing newline still parses`() {
        schedstatContents = ArrayDeque(
            listOf(
                "300000000 5000000 120".toByteArray(),
                "800000000 12000000 150".toByteArray(),
            ),
        )
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        assertEquals("12", tracker.buildAttributes()[SdkInitAttributeKeys.INIT_RUN_DELAY_PCT])
    }

    @Test
    fun `schedstat with fewer than two fields omits run delay but keeps cpu`() {
        schedstatContents = ArrayDeque(listOf("12345\n".toByteArray(), "67890\n".toByteArray()))
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT))
        assertEquals("50", attributes[SdkInitAttributeKeys.INIT_CPU_PCT])
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
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        assertEquals("20", tracker.buildAttributes()[SdkInitAttributeKeys.INIT_RUN_DELAY_PCT])
    }

    @Test
    fun `invalid readings omitted`() {
        cpuTimesMs = ArrayDeque(listOf(130L, 100L))
        schedstatContents = ArrayDeque(listOf("1 9000000 1".toByteArray(), "1 2000000 1".toByteArray()))
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        val attributes = tracker.buildAttributes()
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_CPU_PCT))
        assertFalse(attributes.containsKey(SdkInitAttributeKeys.INIT_RUN_DELAY_PCT))
    }

    @Test
    fun `no attributes when samples were never taken`() {
        assertEquals(emptyMap<String, String>(), createTracker().buildAttributes())
    }

    @Test
    fun `zero-length window omits everything rather than dividing by zero`() {
        wallTimesMs = ArrayDeque(listOf(5000L, 5000L))
        val tracker = createTracker()
        tracker.captureStart()
        tracker.captureEnd()
        assertEquals(emptyMap<String, String>(), tracker.buildAttributes())
    }

    private fun createTracker(
        procFileReader: (String) -> ByteArray? = { path ->
            readPaths.add(path)
            when {
                path.endsWith("schedstat") -> schedstatContents.removeFirst()
                path.endsWith("/io") -> procIoContents.removeFirst()
                else -> null
            }
        },
    ) = SdkInitResourceUsageTracker(
        threadCpuTimeMs = { cpuTimesMs.removeFirst() },
        elapsedRealtimeMs = { wallTimesMs.removeFirst() },
        schedstatPathProvider = { SCHEDSTAT_PATH },
        procFileReader = procFileReader,
        runtimeStatReader = { statName -> runtimeStats[statName]?.removeFirstOrNull() },
    )

    private companion object {
        const val SCHEDSTAT_PATH = "/proc/self/task/123/schedstat"
    }
}
