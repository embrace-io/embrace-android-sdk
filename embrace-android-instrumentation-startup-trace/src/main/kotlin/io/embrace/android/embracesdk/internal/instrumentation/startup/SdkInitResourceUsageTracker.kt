package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_CPU_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_DISK_READ_KB
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_RUN_DELAY_PCT
import java.io.FileInputStream
import kotlin.math.roundToLong

/**
 * Collects metadata about the execution environment during SDK init and provides a set of attributes
 * to contextualize and explain performance anomalies.
 *
 * The job of this is to collect the data required to derive those attributes quickly and efficiently.
 * Turning that captured data into the desired attributes is done in the [buildAttributes] method, which
 * should not be called in perf-sensitive places, as computation and other potentially slow operations
 * could be triggered by that call.
 */
class SdkInitResourceUsageTracker(
    private val threadCpuTimeMs: () -> Long = { SystemClock.currentThreadTimeMillis() },
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val schedstatPathProvider: () -> String = { "/proc/self/task/${Process.myTid()}/schedstat" },
    private val procFileReader: (path: String) -> ByteArray? = ::readProcFile,
    private val runtimeStatReader: (statName: String) -> String? = { statName ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Debug.getRuntimeStat(statName)
        } else {
            null
        }
    },
) {

    @Volatile
    private var schedstatPath: String? = null

    @Volatile
    private var startCpuMs: Long? = null

    @Volatile
    private var endCpuMs: Long? = null

    @Volatile
    private var startWallMs: Long? = null

    @Volatile
    private var endWallMs: Long? = null

    @Volatile
    private var startSchedstat: ByteArray? = null

    @Volatile
    private var endSchedstat: ByteArray? = null

    @Volatile
    private var startProcIo: ByteArray? = null

    @Volatile
    private var endProcIo: ByteArray? = null

    @Volatile
    private var startGcCount: Long? = null

    @Volatile
    private var endGcCount: Long? = null

    /**
     * Captures the state right before the SDK starts. Should be called as close to the SDK start call as possible.
     */
    fun captureStart() {
        try {
            startWallMs = elapsedRealtimeMs()
            startCpuMs = threadCpuTimeMs()
            val path = schedstatPathProvider()
            schedstatPath = path
            startSchedstat = procFileReader(path)
            startProcIo = procFileReader(PROC_SELF_IO_PATH)
            startGcCount = runtimeStatReader(GC_COUNT_STAT)?.toLongOrNull()
        } catch (_: Throwable) {
        }
    }

    /**
     * Captures the state right before the SDK finishes starting. Should be called as close to when SDK startup ends as possible.
     */
    fun captureEnd() {
        try {
            endWallMs = elapsedRealtimeMs()
            endCpuMs = threadCpuTimeMs()
            endSchedstat = schedstatPath?.let(procFileReader)
            endProcIo = procFileReader(PROC_SELF_IO_PATH)
            endGcCount = runtimeStatReader(GC_COUNT_STAT)?.toLongOrNull()
        } catch (_: Throwable) {
        }
    }

    /**
     * Computes attributes from the raw data. Attributes without the valid, requisite raw data will be omitted.
     *
     * The wall interval (the actual perceived sdk init time) comprises three things: time on-CPU ([INIT_CPU_PCT]),
     * time runnable but waiting for a CPU ([INIT_RUN_DELAY_PCT]), and time blocked/sleeping (locks, IO, parks).
     * The values recorded are the most precisely measurable among the three, while the implied third value combines
     * numbers that are harder to accurately obtain, as well as rounding errors.
     *
     * Values are whole percentages of the captured wall interval.
     */
    fun buildAttributes(): Map<String, String> = try {
        buildMap {
            val wallStart = startWallMs
            val wallEnd = endWallMs
            if (wallStart != null && wallEnd != null && wallEnd > wallStart) {
                putSchedulingAttributes(wallMs = wallEnd - wallStart)
                putResourceAttributes()
            }
        }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun MutableMap<String, String>.putSchedulingAttributes(wallMs: Long) {
        deltaOrNull(startCpuMs, endCpuMs)?.let { cpuMs ->
            put(INIT_CPU_PCT, wholePercent(cpuMs, wallMs).toString())
        }
        deltaOrNull(startSchedstat?.let { parseRunDelayNs(it) }, endSchedstat?.let { parseRunDelayNs(it) })?.let { delayNs ->
            put(INIT_RUN_DELAY_PCT, wholePercent(delayNs / 1_000_000L, wallMs).toString())
        }
    }

    private fun MutableMap<String, String>.putResourceAttributes() {
        deltaOrNull(startProcIo?.let { parseReadBytes(it) }, endProcIo?.let { parseReadBytes(it) })?.let { bytes ->
            put(INIT_DISK_READ_KB, (bytes / 1024L).toString())
        }
        deltaOrNull(startGcCount, endGcCount)?.let { count ->
            put(INIT_GC_COUNT, count.toString())
        }
    }

    /**
     * The delta between two samples of a cumulative counter, or null when either sample is
     * missing or the counter went backwards.
     */
    private fun deltaOrNull(start: Long?, end: Long?): Long? = if (start != null && end != null && end >= start) {
        end - start
    } else {
        null
    }

    private fun wholePercent(part: Long, whole: Long): Long = (100.0 * part / whole).roundToLong()

    /**
     * Extracts the cumulative run-delay (second field, in nanoseconds) from raw
     * /proc/<pid>/task/<tid>/schedstat contents: "<running_ns> <run_delay_ns> <timeslices>".
     */
    private fun parseRunDelayNs(raw: ByteArray): Long? = try {
        raw.decodeToString().trim().split(' ').getOrNull(1)?.toLong()
    } catch (_: Throwable) {
        null
    }

    /**
     * Extracts the cumulative read_bytes value from raw /proc/self/io contents (a line-keyed
     * file; counts bytes actually fetched from the storage layer).
     */
    private fun parseReadBytes(raw: ByteArray): Long? = try {
        raw.decodeToString()
            .lineSequence()
            .firstOrNull { it.startsWith("read_bytes:") }
            ?.substringAfter(':')
            ?.trim()
            ?.toLong()
    } catch (_: Throwable) {
        null
    }
}

/**
 * Reads a small procfs file in a single read. This should be fast because it should be backed by memory,
 * not disk.
 */
private fun readProcFile(path: String): ByteArray? = try {
    FileInputStream(path).use { stream ->
        val buffer = ByteArray(PROC_READ_BUFFER_BYTES)
        val count = stream.read(buffer)
        if (count > 0) {
            buffer.copyOf(count)
        } else {
            null
        }
    }
} catch (_: Throwable) {
    null
}

// /proc/self/io is ~120 bytes, so 1024 covers it with room to spare
private const val PROC_READ_BUFFER_BYTES = 1024
private const val PROC_SELF_IO_PATH = "/proc/self/io"
private const val GC_COUNT_STAT = "art.gc.gc-count"
