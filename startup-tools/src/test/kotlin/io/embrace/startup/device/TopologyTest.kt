package io.embrace.startup.device

import io.embrace.startup.cli.ProbeCommand
import io.embrace.startup.core.json.StartupJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The probe's parsers and classifiers on canned device output, and the assembled profile through a scripted adb. */
class TopologyTest {

    @Test
    fun `cpuinfo, cpufreq halves, clusters and little cpus derive as the Python did`() {
        val cpuinfo = "processor\t: 0\nCPU part\t: 0xd05\nprocessor\t: 1\nCPU part\t: 0xd05\n" +
            "processor\t: 4\nCPU part\t: 0xd0a\nBogus line\nprocessor\t: x\nCPU part\t: ignored\n"
        assertEquals(mapOf(0 to "0xd05", 1 to "0xd05", 4 to "0xd0a"), Topology.parseCpuinfo(cpuinfo))

        val (related, freqs) = Topology.splitCpufreqOutput("0 1 2 3\n4 5 6\n7\n1803000\n2348000\n2850000\n")
        assertEquals(listOf("0 1 2 3", "4 5 6", "7"), related)
        val clusters = Topology.deriveClusters(related, freqs, mapOf(0 to "0xd05", 4 to "0xd0a"))
        assertEquals(
            listOf(
                Topology.Cluster(listOf(0, 1, 2, 3), 1803000, "0xd05"),
                Topology.Cluster(listOf(4, 5, 6), 2348000, "0xd0a"),
                Topology.Cluster(listOf(7), 2850000, null),
            ),
            clusters,
        )
        assertEquals(listOf(0, 1, 2, 3) to false, Topology.computeLittleCpus(clusters))
        val homogeneous =
            listOf(Topology.Cluster(listOf(0, 1), 2002000, null), Topology.Cluster(listOf(2, 3), 2002000, null))
        assertEquals(listOf(0, 1) to true, Topology.computeLittleCpus(homogeneous))
        assertEquals(emptyList<Int>() to false, Topology.computeLittleCpus(emptyList()))
        assertEquals(emptyList<String>() to emptyList<String>(), Topology.splitCpufreqOutput(null))
    }

    @Test
    fun `memory, storage, tier and thermal sensors classify as the Python did`() {
        assertEquals(3785, Topology.parseMemTotalMb("MemTotal:        3876352 kB\nMemFree: 1 kB\n"))
        assertNull(Topology.parseMemTotalMb("garbage"))
        assertEquals("go", Topology.classifyRam(1400))
        assertEquals("low", Topology.classifyRam(2048))
        assertEquals("mid", Topology.classifyRam(4000))
        assertEquals("high", Topology.classifyRam(12000))
        assertEquals("unknown", Topology.classifyRam(null))
        assertEquals("ufs-class", Topology.classifyStorage("loop0 sda sdb zram0"))
        assertEquals("emmc-class", Topology.classifyStorage("mmcblk0 mmcblk0rpmb"))
        assertEquals("unknown", Topology.classifyStorage(null))

        val flagship = listOf(
            Topology.Cluster(listOf(0, 1, 2, 3), 1803000, null),
            Topology.Cluster(listOf(4, 5, 6), 2348000, null),
            Topology.Cluster(listOf(7), 2850000, null),
        )
        assertEquals("flagship", Topology.classifyTier(12000, flagship))
        assertEquals("mid", Topology.classifyTier(4000, flagship))
        assertEquals("entry", Topology.classifyTier(2000, flagship))
        assertEquals("entry", Topology.classifyTier(8000, listOf(Topology.Cluster(listOf(0), 1495000, null))))
        assertEquals("unknown", Topology.classifyTier(null, emptyList()))

        val dumpsys = "IsStatusOverride: false\nCurrent temperatures from HAL:\n" +
            "\tTemperature{mValue=31.0, mType=3, mName=battery, mStatus=0}\n" +
            "\tTemperature{mValue=36.2, mType=0, mName=cpu0-silver-usr, mStatus=0}\n" +
            "Current cooling devices from HAL:\n\tCoolingDevice{mValue=0, mType=1, mName=fan}\n"
        assertEquals(listOf("battery", "cpu0-silver-usr"), Topology.parseThermalSensors(dumpsys))
        assertEquals(emptyList<String>(), Topology.parseThermalSensors("no section"))
    }

    @Test
    fun `the assembled profile serialises with the Python's keys and the summary names the little cpus`() {
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                val cmd = args.last()
                val out = when {
                    cmd.startsWith("getprop ro.product.manufacturer") -> "Google\nPixel 3\n12\n31\nQualcomm\nSDM845\nsdm845\n"
                    cmd == "cat /proc/cpuinfo" -> "processor\t: 0\nCPU part\t: 0x803\nprocessor\t: 4\nCPU part\t: 0x802\n"
                    cmd.startsWith("cat /sys/devices/system/cpu/cpufreq") -> "0 1 2 3\n4 5 6 7\n1766400\n2803200\n"
                    cmd == "cat /proc/meminfo" -> "MemTotal:        3785000 kB\n"
                    cmd == "ls /sys/block" -> "sda sdb sdc"
                    cmd == "dumpsys thermalservice" -> "Current temperatures from HAL:\n Temperature{mName=battery, mStatus=0}\n"
                    else -> return Output(1, "", "fail")
                }
                return Output(0, out, "")
            }
        }
        val t = TopologyProbe(adb).probe("8ANX0W1SN", "mid-b")
        assertEquals(31, t.apiLevel)
        assertEquals("Qualcomm", t.socFamily)
        assertEquals("SDM845", t.socModel)
        assertEquals(listOf(0, 1, 2, 3), t.littleCpus)
        assertEquals("mid", t.ramClass)
        assertEquals("ufs-class", t.storageClass)
        assertEquals("mid", t.tier)
        assertEquals(listOf("battery"), t.thermalSensors)
        val json = StartupJson.encodeToString(Topology.serializer(), t)
        listOf(
            "\"serial\"", "\"name\"", "\"vendor\"", "\"model\"", "\"android_release\"", "\"api_level\"", "\"soc_family\"",
            "\"soc_model\"", "\"clusters\"", "\"little_cpus\"", "\"homogeneous\"", "\"ram_mb\"", "\"ram_class\"",
            "\"storage_class\"", "\"tier\"", "\"thermal_sensors\"", "\"max_freq_khz\"", "\"cpu_part\"",
        ).forEach { assertTrue(it, it in json) }
        val summary = ProbeCommand.summary(t, "/out/mid-b-topology.json")
        assertTrue(summary.startsWith("mid-b: Google Pixel 3, Android 12 (API 31); soc Qualcomm SDM845; tier mid; 3696 MB (mid);"))
        assertTrue(summary.contains("--little-cpus 0,1,2,3"))
    }
}
