package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Process
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker.Companion.INIT_CPU_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker.Companion.INIT_RUN_DELAY_PCT
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
                val wallMs = wallEnd - wallStart
                val cpuStart = startCpuMs
                val cpuEnd = endCpuMs
                if (cpuStart != null && cpuEnd != null && cpuEnd >= cpuStart) {
                    put(INIT_CPU_PCT, wholePercent(cpuEnd - cpuStart, wallMs).toString())
                }
                val runDelayStartNs = startSchedstat?.let(::parseRunDelayNs)
                val runDelayEndNs = endSchedstat?.let(::parseRunDelayNs)
                if (runDelayStartNs != null && runDelayEndNs != null && runDelayEndNs >= runDelayStartNs) {
                    val runDelayMs = (runDelayEndNs - runDelayStartNs) / NANOS_PER_MS
                    put(INIT_RUN_DELAY_PCT, wholePercent(runDelayMs, wallMs).toString())
                }
            }
        }
    } catch (_: Throwable) {
        emptyMap()
    }

    companion object {
        /**
         * Percentage of the init window the init thread spent executing on a CPU, rounded to a whole
         * number. It measures the CPU utilization rate on the init thread during SDK init.
         */
        const val INIT_CPU_PCT: String = "init-cpu-pct"

        /**
         * Percentage of the init window the init thread spent runnable but waiting for a CPU,
         * as a whole number. It measures CPU contention from concurrent work.
         */
        const val INIT_RUN_DELAY_PCT: String = "init-run-delay-pct"
    }
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

private const val PROC_READ_BUFFER_BYTES = 128
private const val NANOS_PER_MS = 1_000_000L
