package io.embrace.startup.campaign

import io.embrace.startup.device.Adb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class FleetCampaignTest {

    private val dumpsys = """
        IsStatusOverride: false
        Current temperatures from HAL:
        	Temperature{mValue=36.2, mType=0, mName=cpu0-silver-usr, mStatus=0}
        	Temperature{mValue=37.1, mType=0, mName=cpu1-silver-usr, mStatus=0}
        	Temperature{mValue=40.5, mType=0, mName=cpu4-gold-usr, mStatus=0}
        	Temperature{mValue=41.0, mType=0, mName=cpu5-gold-usr, mStatus=0}
        	Temperature{mValue=31.4, mType=3, mName=skin, mStatus=0}
        	Temperature{mValue=30.0, mType=3, mName=skin2, mStatus=0}
        Current cooling devices from HAL:
        	CoolingDevice{mValue=0, mType=1, mName=fan}
    """.trimIndent()

    @Test
    fun `silicon temps keep three CPU sensors and one skin sensor in HAL order`() {
        assertEquals(
            listOf("cpu0-silver-usr" to 36.2, "cpu1-silver-usr" to 37.1, "cpu4-gold-usr" to 40.5, "skin" to 31.4),
            Thermal.parseSiliconTemps(dumpsys),
        )
        assertEquals(emptyList<Pair<String, Double>>(), Thermal.parseSiliconTemps("nothing here"))
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output = when (args.last()) {
                "thermalservice" -> Output(0, dumpsys, "")
                "battery" -> Output(0, "Current Battery Service state:\n  level: 87\n  temperature: 314\n", "")
                else -> Output(1, "", "")
            }
        }
        assertEquals("silicon: cpu0-silver-usr=36.2 cpu1-silver-usr=37.1 cpu4-gold-usr=40.5 skin=31.4", Thermal.silicon(adb, "s"))
        assertEquals("31.4C level=87", Thermal.battery(adb, "s"))
        assertEquals(40.5, Thermal.maxSilicon(adb, "s")!!, 0.0)
    }

    @Test
    fun `trace directory resolution - exact, normalised, sole-directory fallback, ambiguous, none`() {
        val connected = Files.createTempDirectory("connected")
        val logs = ArrayList<String>()
        val log: (String) -> Unit = { logs.add(it) }

        assertNull(FleetCampaign.resolveTraceDir(connected.resolve("missing"), "x", log))
        assertTrue(logs.last().startsWith("no output directory at "))
        assertNull(FleetCampaign.resolveTraceDir(connected, "x", log))
        assertTrue(logs.last().endsWith("exists but is empty; the run produced no per-device output - aborting"))

        val a14 = Files.createDirectory(connected.resolve("SM-A145M - 15"))
        assertEquals(a14, FleetCampaign.resolveTraceDir(connected, "SM_A145", log))
        assertEquals(a14, FleetCampaign.resolveTraceDir(connected, "Pixel 3", log))
        assertTrue(logs.last().startsWith("hint 'Pixel 3' matched nothing, but exactly one output directory exists ('SM-A145M - 15')"))

        val p3 = Files.createDirectory(connected.resolve("Pixel 3 - 12"))
        assertEquals(p3, FleetCampaign.resolveTraceDir(connected, "pixel3", log))
        assertNull(FleetCampaign.resolveTraceDir(connected, "1", log))
        assertTrue(logs.last().startsWith("hint '1' matches 2 directories ['Pixel 3 - 12', 'SM-A145M - 15']; refusing to guess"))
        assertNull(FleetCampaign.resolveTraceDir(connected, "Galaxy A14", log))
        assertTrue(logs.last().startsWith("hint 'Galaxy A14' matched none of ['Pixel 3 - 12', 'SM-A145M - 15'] under "))
    }

    @Test
    fun `a dry run writes run-metadata and the campaign log without invoking gradle`() {
        val repo = Files.createTempDirectory("repo")
        val app = repo.resolve("examples/ExampleApp")
        Files.createDirectories(app.resolve("gradle"))
        Files.writeString(app.resolve("gradle/libs.versions.toml"), "[versions]\nembrace = \"9.2.0\"\n")
        val camp = repo.resolve("camp")
        var gradleCalls = 0
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output = Output(0, dumpsys, "")
        }
        val rc = FleetCampaign(
            serial = "S1",
            dirMatch = "pixel",
            campDir = camp,
            passes = 2,
            method = "coldStartupBaselineProfile",
            iterations = 20,
            repo = repo,
            adb = adb,
            gradle = { _, _, _ ->
                gradleCalls++
                io.embrace.startup.core.proc.Processes.Output(0, "", "")
            },
            sleep = {},
            dryRun = true,
        ).run()
        assertEquals(0, rc)
        assertEquals(0, gradleCalls)
        val meta = Files.readString(camp.resolve("run-metadata.json"))
        assertTrue(meta.contains("\"serial\":\"S1\""))
        assertTrue(meta.contains("\"sdk_version\":\"9.2.0\""))
        assertTrue(meta.contains("\"catalog_pin\":\"embrace = \\\"9.2.0\\\"\""))
        assertTrue(meta.contains("\"run_shape\":{\"passes\":2,\"iterations\":20}"))
        assertTrue(meta.contains("\"cell\":{\"levels\":{\"compile\":\"profile\"}}"))
        assertTrue(meta.contains("\"device_profile\":{")) // probed through the scripted adb (mostly empty answers)
        val log = Files.readString(camp.resolve("campaign.log"))
        assertTrue(log.contains("dry-run: would run "))
        assertTrue(log.contains("StartupBenchmarks#coldStartupBaselineProfile"))
        assertTrue(log.contains("silicon: cpu0-silver-usr=36.2"))
    }

    @Test
    fun `a pass that fails with the install-timeout signature is retried once, then aborts on a real failure`() {
        val repo = Files.createTempDirectory("repo")
        val app = repo.resolve("examples/ExampleApp")
        Files.createDirectories(app.resolve("gradle"))
        Files.writeString(app.resolve("gradle/libs.versions.toml"), "[versions]\nembrace = \"9.2.0\"\n")
        val connected = app.resolve(FleetCampaign.CONNECTED_SUBPATH)
        val camp = repo.resolve("camp")
        val outcomes = ArrayDeque(
            listOf(
                io.embrace.startup.core.proc.Processes.Output(1, "... Failed to install split APK ...", ""),
                io.embrace.startup.core.proc.Processes.Output(0, "BUILD SUCCESSFUL", ""),
                io.embrace.startup.core.proc.Processes.Output(1, "some real failure", ""),
            ),
        )
        val settings = ArrayList<String>()
        val adb = object : Adb() {
            override fun run(serial: String?, vararg args: String): Output {
                if (args.getOrNull(1) == "settings") {
                    settings.add(args.drop(2).joinToString(" "))
                    return Output(0, if (args[2] == "get") "null" else "", "")
                }
                return Output(0, dumpsys, "")
            }
        }
        val taps = ArrayList<Path>()
        val campaign = FleetCampaign(
            serial = "S1",
            dirMatch = "pixel",
            campDir = camp,
            passes = 2,
            repo = repo,
            adb = adb,
            gradle = { _, _, _ ->
                val out = outcomes.removeFirst()
                if (out.exitCode == 0) {
                    val dir = Files.createDirectories(connected.resolve("Pixel 3 - 12"))
                    Files.writeString(dir.resolve("x_iter000.perfetto-trace"), "t")
                }
                out
            },
            sleep = {},
            logcat = { cmd, file ->
                assertEquals(listOf("adb", "-s", "S1") + Cohorts.LOGCAT_ARGS, cmd)
                taps.add(file)
                // what the tap would have streamed: one launch that created its user session
                Files.writeString(
                    file,
                    "09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 2 1/1 {\"kind\":\"span\",\"name\":\"emb-sdk-init\"," +
                        "\"startNanos\":1,\"endNanos\":2000001,\"attrs\":{\"emb.user_session_id\":\"AAAA\"," +
                        "\"emb.app.version_startup_counter\":\"1\",\"start-first-session-duration-ms\":\"3\"}}\n",
                )
                AutoCloseable {}
            },
        )
        assertEquals(1, campaign.run())
        assertTrue(Files.exists(camp.resolve("pass1-gradle.log")))
        assertTrue(Files.exists(camp.resolve("pass1-gradle-retry.log")))
        assertTrue(Files.exists(camp.resolve("pass1/x_iter000.perfetto-trace")))
        val log = Files.readString(camp.resolve("campaign.log"))
        assertTrue(log.contains("pass 1 hit the install-timeout signature; retrying once"))
        assertTrue(log.contains("pass 1 done in "))
        assertTrue(log.contains("pass 2 FAILED (exit 1); aborting"))
        assertTrue(outcomes.isEmpty())
        assertTrue(Files.isDirectory(Path.of(camp.toString(), "pass1")))

        // cohort verification: the tap was armed once for the campaign and restored after, one capture per
        // pass attempted, the fresh-install launch of a restoring arm is not a violation
        assertEquals(
            listOf(
                "get global embrace_verify_telemetry",
                "put global embrace_verify_telemetry startup:1",
                "delete global embrace_verify_telemetry",
            ),
            settings,
        )
        assertEquals(listOf(camp.resolve("pass1-embverify.log"), camp.resolve("pass2-embverify.log")), taps)
        assertTrue(log.contains("cohort verification: logcat tap armed (embrace_verify_telemetry=startup:1; was 'null')"))
        assertTrue(log.contains("pass 1 cohorts: 1 launches, 1 created, 0 restored, 0 unknown; expected restored; violations: 0"))
        val cohorts = Files.readString(camp.resolve("pass1-cohorts.json"))
        assertTrue(cohorts.contains("\"cohort\":\"created\""))
        assertTrue(cohorts.contains("\"expected\":\"restored\""))
    }

    @Test
    fun `benchmark methods map to the reference recipe's compile-state vocabulary`() {
        assertEquals("profile", FleetCampaign.compileStateOf("coldStartupBaselineProfile"))
        assertEquals("profile", FleetCampaign.compileStateOf("coldStartupBaselineProfileNewUserSession"))
        assertEquals("profile", FleetCampaign.compileStateOf("coldStartupBaselineProfileExpiredUserSession"))
        assertEquals("none", FleetCampaign.compileStateOf("coldStartupNoAot"))
        assertEquals("full", FleetCampaign.compileStateOf("coldStartupFullAot"))
        // CompilationMode.DEFAULT is the fresh-install state, not full AOT (port log #27).
        assertEquals("default", FleetCampaign.compileStateOf("coldStartup"))
        assertNull(FleetCampaign.compileStateOf("somethingElse"))
    }
}
