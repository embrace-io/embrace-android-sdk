package io.embrace.startup.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `device_probe.py`: one connected device's DEVICE PROFILE for a multi-device campaign - what the
 * device is, so numbers stay comparable across sessions, SDK versions and engineers. A marketing
 * name is not a profile.
 *
 * `littleCpus` feeds `--little-cpus` on `variance`, `hypothesis-tests` and `factors-report`, because
 * `outlier_metrics.sql`'s run_cl0/run_cl1 split is a fixed cpu<4 partition that does not know which
 * cluster is actually the little one. Every read is independent so one failure does not abort the
 * probe; an unreadable value is null, never a guess.
 *
 * Note the two RAM vocabularies that coexist in the toolchain, both preserved: this profile's
 * `ram_class` (go/low/mid/high, gating outlier classes) and the reference set's (`<=2GB`, `3-4GB`,
 * `6GB`, `>=8GB`, from `reference_set.py`).
 */
@Serializable
data class Topology(
    val serial: String,
    val name: String,
    val vendor: String?,
    val model: String?,
    @SerialName("android_release") val androidRelease: String?,
    @SerialName("api_level") val apiLevel: Int?,
    @SerialName("soc_family") val socFamily: String?,
    @SerialName("soc_model") val socModel: String?,
    val clusters: List<Cluster>,
    @SerialName("little_cpus") val littleCpus: List<Int>,
    val homogeneous: Boolean,
    @SerialName("ram_mb") val ramMb: Int?,
    @SerialName("ram_class") val ramClass: String,
    @SerialName("storage_class") val storageClass: String,
    val tier: String,
    @SerialName("thermal_sensors") val thermalSensors: List<String>,
) {
    @Serializable
    data class Cluster(
        val cpus: List<Int>,
        @SerialName("max_freq_khz") val maxFreqKhz: Long?,
        @SerialName("cpu_part") val cpuPart: String?,
    )

    /** The `--little-cpus` value to pass downstream: the little cluster, or the Python's `0,1,2,3` fallback. */
    val littleCpusArg: String get() = if (littleCpus.isEmpty()) "0,1,2,3" else littleCpus.sorted().joinToString(",")

    companion object {
        const val TRACING_API_FLOOR: Int = 29

        /** First [count] lines stripped, empty → null, padded with nulls. */
        fun parseProps(text: String?, count: Int): List<String?> {
            val lines = (text ?: "").lines()
            return (0 until count).map { i -> lines.getOrNull(i)?.trim()?.ifEmpty { null } }
        }

        /** `{processor index: CPU part}` from `/proc/cpuinfo`. */
        fun parseCpuinfo(text: String?): Map<Int, String> {
            val parts = LinkedHashMap<Int, String>()
            var cur: Int? = null
            (text ?: "").lines().forEach { line ->
                if (':' !in line) return@forEach
                val key = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                when (key) {
                    "processor" -> cur = value.toIntOrNull()
                    "CPU part" -> cur?.let { parts[it] = value }
                }
            }
            return parts
        }

        /** The concatenated `related_cpus` + `cpuinfo_max_freq` cat output split in half, same policy order. */
        fun splitCpufreqOutput(text: String?): Pair<List<String>, List<String>> {
            if (text.isNullOrEmpty()) return emptyList<String>() to emptyList()
            val lines = text.lines().filter { it.isNotBlank() }
            val half = lines.size / 2
            return lines.take(half) to lines.drop(half)
        }

        fun deriveClusters(related: List<String>, maxFreqs: List<String>, cpuParts: Map<Int, String>): List<Cluster> =
            related.zip(maxFreqs).map { (rel, freq) ->
                val cpus = rel.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() }
                val parsed = if (cpus.all { it != null }) cpus.map { it!! } else emptyList()
                Cluster(parsed, freq.trim().toLongOrNull(), parsed.firstOrNull()?.let { cpuParts[it] })
            }

        /** little = cpus of the lowest-max-frequency cluster(s); one shared frequency = homogeneous, first policy's cpus. */
        fun computeLittleCpus(clusters: List<Cluster>): Pair<List<Int>, Boolean> {
            if (clusters.isEmpty()) return emptyList<Int>() to false
            val freqs = clusters.mapNotNull { it.maxFreqKhz }.toSet()
            if (freqs.size <= 1) return clusters[0].cpus to true
            val min = freqs.min()
            return clusters.filter { it.maxFreqKhz == min }.flatMap { it.cpus } to false
        }

        fun parseMemTotalMb(text: String?): Int? =
            (text ?: "").lines().firstNotNullOfOrNull { MEM_TOTAL.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull() }
                ?.let { (it / KIB_PER_MIB).toInt() }

        /** Coarse ram_class: outlier classes are gated by these boundaries. */
        fun classifyRam(ramMb: Int?): String = when {
            ramMb == null || ramMb == 0 -> "unknown"
            ramMb < RAM_GO -> "go"
            ramMb < RAM_LOW -> "low"
            ramMb < RAM_MID -> "mid"
            else -> "high"
        }

        /** eMMC-class vs UFS/scsi-class from `/sys/block` names; storage class moves IO-stall severity by 10×. */
        fun classifyStorage(lsBlock: String?): String {
            val names = (lsBlock ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                names.any { it.startsWith("sd") } -> "ufs-class"
                names.any { it.startsWith("mmcblk") } -> "emmc-class"
                else -> "unknown"
            }
        }

        /** Deliberately crude entry/mid/flagship from RAM, top cluster clock and cluster count. */
        fun classifyTier(ramMb: Int?, clusters: List<Cluster>): String {
            val topGhz = clusters.mapNotNull { it.maxFreqKhz }.maxOfOrNull { it / KHZ_PER_GHZ } ?: 0.0
            val ram = ramMb ?: 0
            return when {
                ram == 0 && topGhz == 0.0 -> "unknown"
                (ram in 1 until RAM_LOW) || (topGhz > 0.0 && topGhz < ENTRY_GHZ) -> "entry"
                clusters.size >= FLAGSHIP_CLUSTERS && topGhz >= FLAGSHIP_GHZ && ram >= RAM_MID -> "flagship"
                else -> "mid"
            }
        }

        /** `mName` values under "Current temperatures from HAL:" in `dumpsys thermalservice`; never hardcode a sensor. */
        fun parseThermalSensors(text: String?): List<String> {
            if (text.isNullOrEmpty()) return emptyList()
            return halTemperatureLines(text).mapNotNull { line -> M_NAME.find(line)?.groupValues?.get(1)?.trim() }
        }

        /** The lines of the "Current temperatures from HAL:" block of `dumpsys thermalservice`, up to the next section. */
        fun halTemperatureLines(text: String): List<String> {
            val lines = text.lines()
            val start = lines.indexOfFirst { "Current temperatures from HAL:" in it }
            if (start < 0) return emptyList()
            return lines.drop(start + 1).takeWhile { !SECTION_HEADER.containsMatchIn(it) }
        }

        private val MEM_TOTAL = Regex("MemTotal:\\s+(\\d+)\\s*kB")
        private val SECTION_HEADER = Regex("^\\s*Current .*:")
        private val M_NAME = Regex("mName\\s*=\\s*([^,}]+)")
        private const val KIB_PER_MIB = 1024L
        private const val KHZ_PER_GHZ = 1e6
        private const val RAM_GO = 1536
        private const val RAM_LOW = 3072
        private const val RAM_MID = 6144
        private const val ENTRY_GHZ = 2.0
        private const val FLAGSHIP_GHZ = 2.6
        private const val FLAGSHIP_CLUSTERS = 3
    }
}

/** Runs the probe's independent shell reads and assembles a [Topology]. */
class TopologyProbe(private val adb: Adb) {

    /** One `adb shell <command>`; null on any failure, as the Python's `run_shell`. */
    private fun shell(serial: String, command: String): String? = runCatching {
        adb.run(serial, "shell", command).takeIf { it.exitCode == 0 }?.stdout
    }.getOrNull()

    fun probe(serial: String, name: String): Topology {
        val props = Topology.parseProps(
            shell(
                serial,
                "getprop ro.product.manufacturer; getprop ro.product.model; " +
                    "getprop ro.build.version.release; getprop ro.build.version.sdk; " +
                    "getprop ro.soc.manufacturer; getprop ro.soc.model; getprop ro.board.platform",
            ),
            PROP_COUNT,
        )
        val vendor = props[VENDOR_IDX]
        val model = props[MODEL_IDX]
        val android = props[ANDROID_IDX]
        val api = props[API_IDX]
        val socVendor = props[SOC_VENDOR_IDX]
        val socModel = props[SOC_MODEL_IDX]
        val board = props[BOARD_IDX]
        val cpuParts = Topology.parseCpuinfo(shell(serial, "cat /proc/cpuinfo"))
        val (related, maxFreqs) = Topology.splitCpufreqOutput(
            shell(
                serial,
                "cat /sys/devices/system/cpu/cpufreq/policy*/related_cpus " +
                    "/sys/devices/system/cpu/cpufreq/policy*/cpuinfo_max_freq",
            ),
        )
        val clusters = Topology.deriveClusters(related, maxFreqs, cpuParts)
        val (little, homogeneous) = Topology.computeLittleCpus(clusters)
        val ramMb = Topology.parseMemTotalMb(shell(serial, "cat /proc/meminfo"))
        return Topology(
            serial = serial,
            name = name,
            vendor = vendor,
            model = model,
            androidRelease = android,
            apiLevel = api?.takeIf { it.all { c -> c.isDigit() } }?.toInt(),
            socFamily = socVendor ?: board,
            socModel = socModel,
            clusters = clusters,
            littleCpus = little,
            homogeneous = homogeneous,
            ramMb = ramMb,
            ramClass = Topology.classifyRam(ramMb),
            storageClass = Topology.classifyStorage(shell(serial, "ls /sys/block")),
            tier = Topology.classifyTier(ramMb, clusters),
            thermalSensors = Topology.parseThermalSensors(shell(serial, "dumpsys thermalservice")),
        )
    }

    private companion object {
        const val PROP_COUNT = 7
        const val VENDOR_IDX = 0
        const val MODEL_IDX = 1
        const val ANDROID_IDX = 2
        const val API_IDX = 3
        const val SOC_VENDOR_IDX = 4
        const val SOC_MODEL_IDX = 5
        const val BOARD_IDX = 6
    }
}
