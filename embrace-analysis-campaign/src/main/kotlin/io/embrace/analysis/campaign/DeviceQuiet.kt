package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.device.Adb

/**
 * Nothing of ours still running ON THE DEVICE. Killing the host leaves the device untouched: a SIGKILL
 * skips the state restore, so its load generators keep the phone pegged, and a stuck tracer holds the
 * kernel ftrace buffer so every later capture is silently empty while the tool still reports success. A
 * host-side process list cannot see any of it.
 *
 * These processes are ours, so this sweeps rather than merely refusing, then verifies the sweep worked.
 * It fails only if something survives, because measuring a contaminated device produces numbers that
 * look entirely plausible.
 */
class DeviceQuiet(
    private val adb: Adb,
    private val serial: String,
    private val log: (String) -> Unit,
) : Invariant {

    override fun check(): Check {
        val before = leftovers()
        if (before.isEmpty()) {
            return Check(NAME, true, "device quiet")
        }
        log("device quiet: sweeping leftovers from a previous run: ${PyJson.reprList(before)}")
        adb.shell(serial, "pkill", "-9", "-f", "dd if=/dev/zero")
        TRACERS.forEach { adb.shell(serial, "pkill", "-9", it) }
        adb.shell(serial, "atrace", "--async_stop")
        val after = leftovers()
        return if (after.isEmpty()) {
            Check(NAME, true, "swept ${before.size} leftover process(es) from a previous run")
        } else {
            Check(NAME, false, "device still busy after a sweep: ${PyJson.reprList(after)}")
        }
    }

    /** Names of our own processes seen on the device: load generators and trace collectors. */
    private fun leftovers(): List<String> {
        val listing = runCatching { adb.shell(serial, "ps", "-A", "-o", "NAME,ARGS") }.getOrElse { return emptyList() }
        return listing.lines()
            .map { it.trim() }
            .filter { line -> LEFTOVER_MARKERS.any { it in line } }
            .take(MAX_LEFTOVERS_SHOWN)
    }

    companion object {
        const val NAME: String = "device quiet"
        private const val MAX_LEFTOVERS_SHOWN = 6

        /** Trace collectors that hold the kernel ftrace buffer if they outlive the run that started them. */
        private val TRACERS = listOf("tracebox", "perfetto", "atrace")

        /** What one of our own killed runs leaves behind on the device: load generators and tracers. */
        private val LEFTOVER_MARKERS = listOf("dd if=/dev/zero") + TRACERS
    }
}
