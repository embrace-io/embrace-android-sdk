package io.embrace.startup.device

import io.embrace.startup.core.json.DeviceProfile

/**
 * The reference-set PROFILE probe: what a device IS (API level, release,
 * vendor, SoC, cluster topology, RAM class), not which unit it is. Profiles are compared field by
 * field at ingest; an OS upgrade or a replaced handset changes the profile and must become a new
 * device key, because silently comparing across it is the classic way to invent or hide a regression.
 */
class DeviceProbe(private val adb: Adb) {

    fun profile(serial: String): DeviceProfile {
        val clusters = clusters(serial)
        val ram = ramClass(serial)
        return DeviceProfile(
            apiLevel = adb.prop(serial, "ro.build.version.sdk").toIntOrNull() ?: 0,
            release = adb.prop(serial, "ro.build.version.release"),
            vendor = adb.prop(serial, "ro.product.manufacturer"),
            socFamily = socFamily(serial),
            clusters = clusters,
            ramClass = ram,
            storageClass = "unknown", // not reliably readable unrooted; fill in by hand if known
        )
    }

    /** Distinct max frequencies per cpufreq policy, ascending - the cluster topology. */
    fun clusters(serial: String): List<Long> {
        val out = adb.shell(
            serial,
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do cat \$p/cpuinfo_max_freq; done",
        )
        // A whole line, not a number found anywhere: `cat cpuinfo_max_freq` prints the frequency alone,
        // while a failed glob or a busybox complaint lands in the same stream, and any five-digit number
        // inside it (an errno, a pid) would otherwise be read as a CPU cluster.
        return out.lines()
            .mapNotNull { line -> line.trim().takeIf { FREQ.matches(it) }?.toLongOrNull() }
            .toSortedSet()
            .toList()
    }

    fun ramClass(serial: String): String {
        val out = adb.shell(serial, "cat", "/proc/meminfo")
        val kb = MEM_TOTAL.find(out)?.groupValues?.get(1)?.toLongOrNull() ?: return "unknown"
        val gb = kb / (KIB_PER_GIB)
        return when {
            gb <= RAM_2GB -> "<=2GB"
            gb <= RAM_4GB -> "3-4GB"
            gb <= RAM_6GB -> "6GB"
            else -> ">=8GB"
        }
    }

    fun socFamily(serial: String): String {
        listOf("ro.soc.model", "ro.board.platform", "ro.hardware").forEach { name ->
            val value = adb.prop(serial, name)
            if (value.isNotEmpty() && value.lowercase() != "unknown") return value
        }
        return "unknown"
    }

    companion object {
        /** A coarse hint only - the operator confirms it. Tier is user-visible class, which no property captures. */
        fun tierGuess(ram: String, clusters: List<Long>): String {
            val distinct = if (clusters.isEmpty()) 1 else clusters.toSet().size
            return when {
                ram == "<=2GB" -> "entry"
                ram == "3-4GB" && distinct <= 2 -> "entry-mid"
                distinct >= FLAGSHIP_CLUSTERS -> "flagship"
                else -> "mid"
            }
        }

        private val FREQ = Regex("\\d{5,}")
        private val MEM_TOTAL = Regex("MemTotal:\\s+(\\d+)")
        private const val KIB_PER_GIB = 1024.0 * 1024.0
        private const val RAM_2GB = 2.2
        private const val RAM_4GB = 4.5
        private const val RAM_6GB = 7.0
        private const val FLAGSHIP_CLUSTERS = 3
    }
}
