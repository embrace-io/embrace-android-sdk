package io.embrace.analysis.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure tier heuristic ([DeviceProbe.tierGuess]) and its three device-property
 * readers (`clusters`, `ramClass`, `socFamily`). Unlike [Topology]'s helpers, these are not anchored
 * string-only parsers - each takes a serial and calls `adb.shell`/`adb.prop` itself - so a scripted
 * [Adb] stands in for the shell output they parse, exactly as [TopologyTest] drives [TopologyProbe];
 * no attached device is needed.
 */
class DeviceProbeTest {

    @Test
    fun `clusters extracts distinct max frequencies ascending from the policy loop's cat output`() {
        val probe = DeviceProbe(fakeAdb(cpufreq = "1804800\n1804800\n2803200\n"))

        assertEquals(listOf(1804800L, 2803200L), probe.clusters("SERIAL0001"))
    }

    @Test
    fun `clusters returns empty when the policy glob fails and the shell error has no digits`() {
        val probe =
            DeviceProbe(fakeAdb(cpufreq = "sh: /sys/devices/system/cpu/cpufreq/policy*: No such file or directory\n"))

        assertEquals(emptyList<Long>(), probe.clusters("SERIAL0001"))
    }

    /**
     * A shell complaint shares the stream with the frequencies, and a number inside it used to be read as
     * a cluster: the probe would then invent a topology, which decides the device's tier and lands in the
     * reference set as though it had been measured.
     */
    @Test
    fun `clusters ignores a number embedded in a shell error rather than reading it as a frequency`() {
        val probe = DeviceProbe(
            fakeAdb(cpufreq = "cat: /sys/devices/system/cpu/cpufreq/policy9/cpuinfo_max_freq: No such file (errno 12345)\n"),
        )

        assertEquals(emptyList<Long>(), probe.clusters("SERIAL0001"))
    }

    @Test
    fun `clusters reads the frequencies that share a stream with an error line`() {
        val probe =
            DeviceProbe(fakeAdb(cpufreq = "1804800\ncat: policy9/cpuinfo_max_freq: No such file (errno 12345)\n2803200\n"))

        assertEquals(listOf(1804800L, 2803200L), probe.clusters("SERIAL0001"))
    }

    @Test
    fun `ramClass classifies a realistic MemTotal line`() {
        val probe = DeviceProbe(fakeAdb(meminfo = "MemTotal:        3971000 kB\nMemFree: 1 kB\n"))

        assertEquals("3-4GB", probe.ramClass("SERIAL0001"))
    }

    @Test
    fun `ramClass returns unknown when MemTotal cannot be found in the output at all`() {
        val probe = DeviceProbe(fakeAdb(meminfo = "garbage, no meminfo here"))

        assertEquals("unknown", probe.ramClass("SERIAL0001"))
    }

    @Test
    fun `ramClass boundaries sit in the lower class exactly at the cutoff, and move up one kB later`() {
        assertEquals("<=2GB", ramClassFor(RAM_2GB_KB))
        assertEquals("3-4GB", ramClassFor(RAM_2GB_KB + 1))
        assertEquals("3-4GB", ramClassFor(RAM_4GB_KB))
        assertEquals("6GB", ramClassFor(RAM_4GB_KB + 1))
        assertEquals("6GB", ramClassFor(RAM_6GB_KB))
        assertEquals(">=8GB", ramClassFor(RAM_6GB_KB + 1))
    }

    @Test
    fun `socFamily takes the first non-empty property in ro-soc-model, ro-board-platform, ro-hardware order`() {
        val probe = DeviceProbe(
            fakeAdb(props = mapOf("ro.soc.model" to "SM8350", "ro.board.platform" to "lahaina", "ro.hardware" to "qcom")),
        )

        assertEquals("SM8350", probe.socFamily("SERIAL0001"))
    }

    @Test
    fun `socFamily skips a literal empty value and a case-insensitive unknown before falling through`() {
        val probe = DeviceProbe(
            fakeAdb(props = mapOf("ro.soc.model" to "", "ro.board.platform" to "Unknown", "ro.hardware" to "qcom")),
        )

        assertEquals("qcom", probe.socFamily("SERIAL0001"))
    }

    @Test
    fun `socFamily falls back to unknown when every property is blank or missing`() {
        val probe = DeviceProbe(fakeAdb())

        assertEquals("unknown", probe.socFamily("SERIAL0001"))
    }

    @Test
    fun `tierGuess table - low RAM wins over cluster count, and the flagship threshold is exactly 3 distinct frequencies`() {
        assertEquals("entry", DeviceProbe.tierGuess("<=2GB", emptyList()))
        assertEquals("entry", DeviceProbe.tierGuess("<=2GB", listOf(1L, 2L, 3L, 4L))) // low RAM dominates even 4 distinct clusters
        assertEquals("entry-mid", DeviceProbe.tierGuess("3-4GB", listOf(1L, 2L)))
        assertEquals("entry-mid", DeviceProbe.tierGuess("3-4GB", listOf(5L, 5L, 5L))) // one distinct freq despite 3 entries
        assertEquals(
            "flagship", // surprising: 3-4GB RAM with 3 distinct clusters is called "flagship", never "entry-mid" or "mid"
            DeviceProbe.tierGuess("3-4GB", listOf(1L, 2L, 3L)),
        )
        assertEquals("mid", DeviceProbe.tierGuess("6GB", listOf(1L, 2L)))
        assertEquals("flagship", DeviceProbe.tierGuess("6GB", listOf(1L, 2L, 3L)))
        assertEquals("mid", DeviceProbe.tierGuess(">=8GB", emptyList())) // no cluster data at all still lands on "mid", not "flagship"
        assertEquals("flagship", DeviceProbe.tierGuess(">=8GB", listOf(1L, 2L, 3L)))
        assertEquals("mid", DeviceProbe.tierGuess("unknown", emptyList())) // tierGuess never returns "unknown" itself
    }

    private fun ramClassFor(kb: Long): String = DeviceProbe(fakeAdb(meminfo = "MemTotal:        $kb kB\n")).ramClass("SERIAL0001")

    private fun fakeAdb(cpufreq: String = "", meminfo: String = "", props: Map<String, String> = emptyMap()): Adb = object : Adb() {
        override fun run(serial: String?, vararg args: String): Output {
            val last = args.last()
            val out = when {
                last.startsWith("for p in /sys/devices/system/cpu/cpufreq") -> cpufreq
                last == "/proc/meminfo" -> meminfo
                else -> props[last] ?: ""
            }
            return Output(0, out, "")
        }
    }

    private companion object {
        const val RAM_2GB_KB = 2306867L
        const val RAM_4GB_KB = 4718592L
        const val RAM_6GB_KB = 7340032L
    }
}
