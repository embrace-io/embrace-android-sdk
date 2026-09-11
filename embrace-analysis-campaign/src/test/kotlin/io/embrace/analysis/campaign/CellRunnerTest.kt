package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.proc.Processes
import io.embrace.analysis.device.Adb
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CellRunnerTest {

    @Test
    fun `parsers - temperatures, resolved sdk line, dexopt status, cell dir name`() {
        val dumpsys = "Cached temperatures:\n\tTemperature{mValue=72.6, mType=0, mName=cpu3-gold-usr, mStatus=0}\n" +
            "HAL connection:\n\tok\nCurrent temperatures from HAL:\n" +
            " Temperature{mValue=36.2, mType=0, mName=cpu0, mStatus=0}\n" +
            "Temperature{mValue=31.0, mType=3, mName=skin, mStatus=0}\nCurrent cooling devices from HAL:\n  CoolingDevice{mValue=0}\n"
        assertEquals(listOf(36.2, 31.0), Temperature.parseTemperatures(dumpsys)) // the cached 72.6 peak is excluded
        assertEquals(
            "io.embrace:embrace-android-sdk:9.2.0",
            SdkMatch.resolvedSdkLine("benchmarkRuntimeClasspath\n+--- io.embrace:embrace-android-sdk:9.2.0\n|    \\--- x\n"),
        )
        assertNull(SdkMatch.resolvedSdkLine("nothing"))
        val dexopt = "Dexopt state:\n  [com.other]\n    path: /x\n      status=speed\n  [io.embrace.android.exampleapp]\n" +
            "    path: /data/app/x/base.apk\n      arm64: [status=speed-profile] [reason=install]\n  [com.next]\n      status=verify\n"
        assertEquals("arm64: [status=speed-profile] [reason=install]", CompileState.dexoptStatus(dexopt, CellRunner.PKG))
        assertNull(CompileState.dexoptStatus("Dexopt state:\n  [com.other]\n      status=speed\n", CellRunner.PKG))
        assertEquals("mid__9.2.0__compile-none_install-fresh", CellRunner.cellDirName("mid|9.2.0|compile=none,install=fresh"))
    }

    /**
     * A killed run leaves its load generators and tracers running ON THE DEVICE, where no host-side
     * process list can see them. The next cell would then measure a pegged phone, or capture traces a
     * stuck tracer has emptied, and report success either way.
     */
    @Test
    fun `device-quiet invariant sweeps a killed run's leftovers and passes once they are gone`() {
        val issued = ArrayList<String>()
        var listing = "root 1 dd if=/dev/zero of=/dev/null\nshell 2 tracebox traced_probes\nroot 3 zygote64\n"
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                val key = args.joinToString(" ")
                issued.add(key)
                if (key.startsWith("shell pkill") || key.startsWith("shell atrace")) listing = "root 3 zygote64\n"
                return Output(0, if (key.startsWith("shell ps")) listing else "", "")
            }
        }

        val check = DeviceQuiet(adb, "SERIAL0001") { }.check()

        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("swept 2 leftover"))
        assertTrue("sweeps the load generators", issued.any { it.contains("pkill") && it.contains("dd if=/dev/zero") })
        assertTrue("stops a stuck tracer", issued.any { it.contains("tracebox") })
        assertTrue("releases the ftrace buffer", issued.any { it.contains("atrace --async_stop") })
    }

    @Test
    fun `device-quiet invariant fails when a leftover survives the sweep`() {
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                val key = args.joinToString(" ")
                val listing = "root 1 dd if=/dev/zero of=/dev/null\n"
                return Output(0, if (key.startsWith("shell ps")) listing else "", "")
            }
        }

        val check = DeviceQuiet(adb, "SERIAL0001") { }.check()

        assertTrue(check.detail, !check.ok)
        assertTrue(check.detail, check.detail.contains("still busy after a sweep"))
    }

    @Test
    fun `a device running nothing of ours is quiet without a sweep`() {
        val issued = ArrayList<String>()
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                issued.add(args.joinToString(" "))
                return Output(0, "root 3 zygote64\nsystem 4 system_server\n", "")
            }
        }

        val check = DeviceQuiet(adb, "SERIAL0001") { }.check()

        assertTrue(check.detail, check.ok)
        assertEquals("device quiet", check.detail)
        assertTrue("nothing is killed on a clean device", issued.none { it.contains("pkill") })
    }

    @Test
    fun `check-only run passes every invariant against scripted adb and gradle and writes cell-state json`() {
        val repo = Files.createTempDirectory("repo")
        Files.createDirectories(repo.resolve("examples/ExampleApp/gradle"))
        Files.writeString(repo.resolve("examples/ExampleApp/gradle/libs.versions.toml"), "embrace = \"9.2.0\"\n")
        Files.writeString(repo.resolve("gradle.properties"), "version=9.3.0-SNAPSHOT\n")
        val cells = repo.resolve("cells.json")
        Files.writeString(
            cells,
            """{"plan": {"run_id": "t1", "passes": 1, "iterations": 20, "build_type": "benchmark", "instrument": "emb-sdk-start",
                 "devices": {"mid-b": {"serial": "SERIAL0001", "tier": "entry-mid", "cool_gate_c": 32.0, "profile": {"api_level": 31}}}},
                "cells": [{"id": "mid-b|9.2.0|reference", "version": "9.2.0", "levels": {"compile": "profile"}, "device": "mid-b",
                           "group": "version-sweep"}]}""",
        )
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                val key = args.joinToString(" ")
                val out = when {
                    key == "devices" -> "List of devices attached\nSERIAL0001\tdevice\n"
                    key == "shell dumpsys thermalservice" ->
                        "Current temperatures from HAL:\n\tTemperature{mValue=30.5, mType=0, mName=cpu0, mStatus=0}\n"
                    key == "shell pm list packages io.embrace.android.exampleapp" -> "package:io.embrace.android.exampleapp\n"
                    key == "shell dumpsys package dexopt" ->
                        "  [io.embrace.android.exampleapp]\n      arm64: [status=speed-profile] [reason=install]\n"
                    else -> ""
                }
                return Output(0, out, "")
            }
        }
        val runner = CellRunner(
            repo = repo,
            adb = adb,
            gradle = { _, _ -> Processes.Output(0, "+--- io.embrace:embrace-android-sdk:9.2.0\n", "") },
            processList = {
                // the launching shell's own command line names cell-runner and must NOT count
                "  ${ProcessHandle.current().parent().map { it.pid() }.orElse(1)} /bin/zsh -c tools/startup cell-runner --cells x\n" +
                    "  100 /bin/zsh\n  200 java startup-tools fleet-campaign --serial X\n"
            },
            campaign = { _, _, _, _, _ -> error("must not run passes in check-only") },
            lockFile = repo.resolve("lock.pid"),
            traceProcessor = null,
        )
        // Another campaign is alive on the host: invariants refuse.
        val refused = runCatching {
            runner.run(cells, "mid-b|9.2.0|reference", repo.resolve("out"), checkOnly = true)
        }.exceptionOrNull()
        assertTrue(refused is CellRunner.Abort)
        assertEquals("ABORT: invariants failed: ['host quiet'] - fix, do not proceed", refused!!.message)

        val quiet = CellRunner(
            repo = repo,
            adb = adb,
            gradle = { _, _ -> Processes.Output(0, "+--- io.embrace:embrace-android-sdk:9.2.0\n", "") },
            processList = { "  100 /bin/zsh\n" },
            campaign = { _, _, _, _, _ -> error("must not run passes in check-only") },
            lockFile = repo.resolve("lock.pid"),
            traceProcessor = null,
        )
        val cellDir = quiet.run(cells, "mid-b|9.2.0|reference", repo.resolve("out"), checkOnly = true)
        assertEquals(repo.resolve("out/mid-b__9.2.0__reference"), cellDir)
        val state = StartupJson.parseToJsonElement(Files.readString(cellDir.resolve("cell-state.json"))).jsonObject
        assertEquals("t1", state.getValue("plan_run_id").jsonPrimitive.content)
        assertEquals("SERIAL0001", state.getValue("serial").jsonPrimitive.content)
        assertEquals("entry-mid", state.getValue("device_profile").jsonObject.getValue("tier").jsonPrimitive.content)
        assertTrue("serial" !in state.getValue("device_profile").jsonObject)
        assertEquals("embrace = \"9.2.0\"", state.getValue("catalog_pin").jsonPrimitive.content)
        val checks = state.getValue("checks").jsonObject
        assertEquals("host quiet", checks.getValue("host quiet").jsonPrimitive.content)
        assertEquals("expected 9.2.0, resolved io.embrace:embrace-android-sdk:9.2.0", checks.getValue("sdk matches").jsonPrimitive.content)
        assertEquals("hottest sensor 30.5 C <= gate 32.0 C", checks.getValue("temperature").jsonPrimitive.content)
        assertEquals("arm64: [status=speed-profile] [reason=install]", checks.getValue("compile state").jsonPrimitive.content)
        val log = Files.readString(cellDir.resolve("cell.log"))
        assertTrue(log.contains("[cell] check-only: invariants passed; not running passes"))
        assertTrue(!Files.exists(repo.resolve("lock.pid")))

        val unknown = runCatching { quiet.run(cells, "nope", repo.resolve("out"), true) }.exceptionOrNull()
        assertEquals("no such cell: nope", unknown?.message)
    }

    @Test
    fun `temperature and compile checks word their verdicts like the golden`() {
        val adb = object : Adb() {
            var thermal = ""
            override fun run(serial: String?, vararg args: String): Output = Output(0, thermal, "")
        }
        adb.thermal = "Current temperatures from HAL:\nTemperature{mValue=45.5, mType=0, mName=cpu0, mStatus=0}\n" +
            "Temperature{mValue=0.0, mType=0, mName=bogus, mStatus=0}\n"
        assertEquals(
            Check("temperature", false, "hottest sensor 45.5 C > gate 32.0 C"),
            Temperature(adb, "s", 32.0, null).check(),
        )
        assertEquals(
            Check("temperature", true, "hot cell: hottest sensor 45.5 C (band enforced by the heater)"),
            Temperature(adb, "s", 32.0, "hot").check(),
        )
        adb.thermal = "Current temperatures from HAL:\nTemperature{mValue=0.0, mType=0, mName=bogus, mStatus=0}\n"
        assertEquals(
            Check("temperature", false, "no plausible thermalservice sensor values (do not fall back to battery)"),
            Temperature(adb, "s", 32.0, null).check(),
        )
        adb.thermal = "  [io.embrace.android.exampleapp]\n      arm64: [status=verify] [reason=install]\n"
        assertEquals(
            Check("compile state", true, "arm64: [status=verify] [reason=install]"),
            CompileState(adb, "s", CellRunner.PKG, "none").check(),
        )
        assertEquals(
            Check("compile state", false, "arm64: [status=verify] [reason=install]"),
            CompileState(adb, "s", CellRunner.PKG, "profile").check(),
        )
        assertEquals(
            Check("compile state", false, "arm64: [status=verify] [reason=install]"),
            CompileState(adb, "s", CellRunner.PKG, "full").check(),
        )
    }
}
