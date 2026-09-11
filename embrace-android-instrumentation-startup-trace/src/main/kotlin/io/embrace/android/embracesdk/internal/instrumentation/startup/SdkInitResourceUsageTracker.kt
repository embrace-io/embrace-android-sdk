package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_CPU_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_DISK_READ_KB
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_RUN_DELAY_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.NUMERIC_ERROR
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
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
    private val logger: InternalLogger,
    private val versionChecker: VersionChecker = BuildVersionChecker,
    private val threadCpuTimeMs: () -> Long = { SystemClock.currentThreadTimeMillis() },
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val schedstatPathProvider: () -> String = { "/proc/self/task/${Process.myTid()}/schedstat" },
    private val procFileReader: (path: String) -> ByteArray? = ::readProcFile,
    private val runtimeStatReader: (statName: String) -> String? = { statName ->
        if (versionChecker.isAtLeast(INIT_GC_COUNT_MIN_API)) {
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
    private var startGcCount: String? = null

    @Volatile
    private var endGcCount: String? = null

    /**
     * Captures the state right before the SDK starts. Should be called as close to the SDK start call as possible.
     */
    fun captureStart() {
        startWallMs = safeRead(elapsedRealtimeMs)
        startCpuMs = safeRead(threadCpuTimeMs)
        schedstatPath = safeRead(schedstatPathProvider)
        startSchedstat = readProc(schedstatPath)
        startProcIo = readProc(PROC_SELF_IO_PATH)
        startGcCount = safeRead { runtimeStatReader(GC_COUNT_STAT) }
    }

    /**
     * Captures the state right before the SDK finishes starting. Should be called as close to when SDK startup ends as possible.
     */
    fun captureEnd() {
        endWallMs = safeRead(elapsedRealtimeMs)
        endCpuMs = safeRead(threadCpuTimeMs)
        endSchedstat = readProc(schedstatPath)
        endProcIo = readProc(PROC_SELF_IO_PATH)
        endGcCount = safeRead { runtimeStatReader(GC_COUNT_STAT) }
    }

    /**
     * Computes attributes from the raw data captured during init. An attribute whose data this device does not
     * provide is left out (i.e. the attribute is not recorded). If for an attribute, this device ought to provide the
     * data, but we still failed to compute its value, the attribute is written with a sentinel value that is outside
     * the valid range to denote the failure.
     */
    fun buildAttributes(): Map<String, String> = buildMap {
        safePutAttributes(logger, INIT_WINDOW_ATTRIBUTES) { putInitWindowShares() }
        safePutAttributes(logger, INIT_DISK_READ_KB) { putDiskReadKb() }
        safePutAttributes(logger, INIT_GC_COUNT) { putGcCount() }
    }

    /**
     * Record attributes that represent the shares of the init window which were associated with some known state or work.
     */
    private fun MutableMap<String, String>.putInitWindowShares() {
        val wallStart = startWallMs
        val wallEnd = endWallMs

        // Record an error when we can't determine the SDK init duration because the requisite date is missing or incoherent
        if (wallStart == null || wallEnd == null || wallEnd < wallStart) {
            put(INIT_CPU_PCT, NUMERIC_ERROR)
            put(INIT_RUN_DELAY_PCT, NUMERIC_ERROR)
            logger.trackAttributeError(INIT_WINDOW_ATTRIBUTES)
        } else if (wallEnd != wallStart) {
            // Only try to compute a percentage if the denominator, the SDK init time, isn't 0.
            val wallMs = wallEnd - wallStart
            putCpuPct(wallMs)
            putRunDelayPct(wallMs)
        }
    }

    private fun MutableMap<String, String>.putCpuPct(wallMs: Long) {
        monotonicCounterDelta(key = INIT_CPU_PCT, start = startCpuMs, end = endCpuMs)?.let { cpuMs ->
            put(INIT_CPU_PCT, wholePercent(cpuMs, wallMs).toString())
        }
    }

    private fun MutableMap<String, String>.putRunDelayPct(wallMs: Long) {
        val start = startSchedstat
        val end = endSchedstat
        if (start != null && end != null) {
            monotonicCounterDelta(
                key = INIT_RUN_DELAY_PCT,
                start = parseRunDelayNs(start),
                end = parseRunDelayNs(end),
            )?.let { delayNs ->
                put(INIT_RUN_DELAY_PCT, wholePercent(delayNs / 1_000_000L, wallMs).toString())
            }
        }
    }

    private fun MutableMap<String, String>.putDiskReadKb() {
        val start = startProcIo
        val end = endProcIo
        if (start != null && end != null) {
            monotonicCounterDelta(
                key = INIT_DISK_READ_KB,
                start = parseReadBytes(start),
                end = parseReadBytes(end),
            )?.let { bytes ->
                put(INIT_DISK_READ_KB, (bytes / 1024L).toString())
            }
        }
    }

    private fun MutableMap<String, String>.putGcCount() {
        if (versionChecker.isAtLeast(INIT_GC_COUNT_MIN_API)) {
            val start = startGcCount
            val end = endGcCount
            if (start != null && end != null) {
                monotonicCounterDelta(
                    key = INIT_GC_COUNT,
                    start = start.toLongOrNull(),
                    end = end.toLongOrNull(),
                )?.let { count ->
                    put(INIT_GC_COUNT, count.toString())
                }
            }
        }
    }

    /**
     * Return the increase of a counter as tracked by [start] and [end]. If either number is missing or there was a decrease,
     * return null, as well as put an entry in the map with the key [key] and the value [NUMERIC_ERROR] to signify the failure
     * attempt.
     */
    private fun MutableMap<String, String>.monotonicCounterDelta(key: String, start: Long?, end: Long?): Long? =
        if (start == null || end == null || end < start) {
            put(key, NUMERIC_ERROR)
            logger.trackAttributeError(key)
            null
        } else {
            end - start
        }

    /**
     * Extracts the cumulative run-delay (second field, in nanoseconds) from raw
     * /proc/<pid>/task/<tid>/schedstat contents: "<running_ns> <run_delay_ns> <timeslices>".
     */
    private fun parseRunDelayNs(raw: ByteArray): Long? = safeRead {
        raw.decodeToString().trim().split(' ').getOrNull(1)?.toLong()
    }

    /**
     * Extracts the cumulative read_bytes value from raw /proc/self/io contents (a line-keyed
     * file; counts bytes actually fetched from the storage layer).
     */
    private fun parseReadBytes(raw: ByteArray): Long? = safeRead {
        raw.decodeToString()
            .lineSequence()
            .firstOrNull { it.startsWith("read_bytes:") }
            ?.substringAfter(':')
            ?.trim()
            ?.toLong()
    }

    private fun readProc(path: String?): ByteArray? = path?.let { safeRead { procFileReader(it) } }

    private fun wholePercent(part: Long, whole: Long): Long = (100.0 * part / whole).roundToLong()

    private fun <T> safeRead(read: () -> T): T? = runCatching { read() }.getOrNull()
}

/**
 * Reads a small procfs file in a single read. This should be fast because it should be backed by memory,
 * not disk.
 */
private fun readProcFile(path: String): ByteArray? = runCatching {
    FileInputStream(path).use { stream ->
        val buffer = ByteArray(PROC_READ_BUFFER_BYTES)
        val count = stream.read(buffer)
        if (count > 0) {
            buffer.copyOf(count)
        } else {
            null
        }
    }
}.getOrNull()

// /proc/self/io is ~120 bytes, so 1024 covers it with room to spare
private const val PROC_READ_BUFFER_BYTES = 1024
private const val PROC_SELF_IO_PATH = "/proc/self/io"
private const val GC_COUNT_STAT = "art.gc.gc-count"
private const val INIT_WINDOW_ATTRIBUTES = "init-cpu-shares"
