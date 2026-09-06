package io.embrace.startup.store

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.device.Adb
import io.embrace.startup.device.DeviceProvenance
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Corpus submission: the allowlist, the admissibility rules on real store records, and device
 * provenance collected through a scripted `adb`. A finding this test pins down: every record in the
 * real longitudinal store is INADMISSIBLE under the Python's own rule, because their signal
 * inventories were never taken from a clean trace (the composed-window canary defect).
 */
class SubmitTest {

    private val store: Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("longitudinal/store.jsonl")

    @Test
    fun `real store records are found by run id and refused for their unclean signal inventory`() {
        val local = assertNotNull(Submit.findLocal(store, "9.2.0__mid-b")).let { Submit.findLocal(store, "9.2.0__mid-b")!! }
        assertNull(Submit.findLocal(store, "nope"))
        val out = Submit.build(local, "hanson", "9.2.0__mid-b", "2026-09-02T08:00:00", device = null, lossyTolerancePct = 0.0)
        assertNull(out.record)
        assertEquals(
            listOf(
                "the signal inventory did not come from a clean trace (event-parse errors), so signals_present cannot be " +
                    "distinguished from a genuine absence - re-capture, or submit with signals_present omitted",
            ),
            out.problems,
        )
    }

    @Test
    fun `an admissible record carries exactly the allowlisted fields plus device provenance, and previews without windows`() {
        val local = JsonObject(
            mapOf(
                "run_id" to JsonPrimitive("r1"),
                "sdk_version" to JsonPrimitive("9.2.0"),
                "app_build_id" to JsonPrimitive("abc"),
                "recipe" to JsonObject(
                    mapOf(
                        "build_type" to JsonPrimitive("benchmark"),
                        "compile_state" to JsonPrimitive("profile"),
                        "instrument" to JsonPrimitive("emb-sdk-start"),
                    ),
                ),
                "conditions" to JsonObject(mapOf("compile" to JsonPrimitive("profile"))),
                "derived" to JsonObject(mapOf("n" to JsonPrimitive(3), "median" to JsonPrimitive(2.0))),
                "windows_ms" to JsonArray(listOf(1.0, 2.0, 3.0).map { JsonPrimitive(it) }),
                "signals_present" to JsonArray(listOf(JsonPrimitive("emb-sdk-start"))),
                "trace_health" to JsonObject(
                    mapOf(
                        "traces" to JsonPrimitive(3),
                        "buffer_loss" to JsonPrimitive(0),
                        "parse_errors" to JsonPrimitive(0),
                        "signals_from_clean_trace" to JsonPrimitive(true),
                    ),
                ),
                "device_key" to JsonPrimitive("mid-b"), // NOT allowlisted: must not leak
                "device_profile" to JsonObject(mapOf("api_level" to JsonPrimitive(31))), // NOT allowlisted
                "notes" to JsonPrimitive(""),
            ),
        )
        val tmp = Files.createTempDirectory("corpus")
        val device = DeviceProvenance(ScriptedAdb()).collect("SERIAL123", tmp.resolve("corpus.jsonl"))
        val out = Submit.build(local, "alice", "r1", "2026-09-02T08:00:00", device, 0.0)
        assertEquals(emptyList<String>(), out.problems)
        val rec = out.record!!
        assertEquals(
            listOf(
                "schema_version", "submission_id", "submitted_at", "contributor", "sdk_version", "app_build_id", "recipe",
                "conditions", "derived", "windows_ms", "signals_present", "trace_health", "notes", "unit_id", "model",
                "os_build", "api_level", "security_patch", "skin_version", "kernel_version", "soc_family", "storage_free_pct",
                "battery_health", "installed_app_count", "device_settings",
            ),
            rec.keys.toList(),
        )
        assertEquals("alice-r1", rec.getValue("submission_id").jsonPrimitive.content)
        assertEquals("Pixel 3", rec.getValue("model").jsonPrimitive.content)
        assertEquals("<50", rec.getValue("installed_app_count").jsonPrimitive.content)
        assertEquals("60-80%", rec.getValue("storage_free_pct").jsonPrimitive.content)
        assertEquals("2", rec.getValue("battery_health").jsonPrimitive.content)
        assertEquals("0.5", rec.getValue("device_settings").jsonObject.getValue("animation_scale").jsonPrimitive.content)
        val token = rec.getValue("unit_id").jsonPrimitive.content
        assertEquals(16, token.length)
        assertTrue("SERIAL123" !in Submit.preview(rec))
        // Same serial, same salt file -> same token; the salt persisted beside the corpus.
        assertEquals(token, DeviceProvenance(ScriptedAdb()).unitToken("SERIAL123", tmp.resolve("corpus.jsonl")))
        assertTrue(Files.exists(tmp.resolve(".unit-salt")))
        val preview = Submit.preview(rec)
        assertTrue(preview.contains("(+ 3 per-iteration window values)"))
        assertTrue(!preview.contains("\"windows_ms\""))
        assertTrue(preview.endsWith("redaction check: no serial, no package names, no paths, no raw traces in the above."))

        val corpus = tmp.resolve("corpus.jsonl")
        Submit.append(corpus, rec)
        Submit.append(corpus, rec)
        assertEquals(2, Files.readAllLines(corpus).size)
    }

    @Test
    fun `the other admissibility rules fire with the Python wording`() {
        val local = JsonObject(
            mapOf(
                "sdk_version" to JsonPrimitive("9.3.0-SNAPSHOT"),
                "recipe" to JsonObject(mapOf("build_type" to JsonPrimitive("benchmark"))),
                "trace_health" to JsonObject(
                    mapOf(
                        "traces" to JsonPrimitive(200),
                        "buffer_loss" to JsonPrimitive(10),
                        "signals_from_clean_trace" to JsonPrimitive(true),
                    ),
                ),
            ),
        )
        val out = Submit.build(local, "c", "r", "t", device = JsonObject(mapOf("os_build" to JsonPrimitive(""))), lossyTolerancePct = 2.0)
        assertEquals(
            listOf(
                "10/200 traces (5%) lost written data, above the 2% tolerance - the submitted distribution may be built " +
                    "on evicted windows",
                "sdk_version '9.3.0-SNAPSHOT' is not a published artifact - working-tree builds are not reproducible by " +
                    "others and are inadmissible",
                "no derived statistics in the local record",
                "recipe.compile_state missing - the cell key would be incomplete",
                "recipe.instrument missing - the cell key would be incomplete",
                "could not read the OS build fingerprint - same api_level is NOT the same software, so the record would not be groupable",
            ),
            out.problems,
        )
        assertEquals("<50", DeviceProvenance.bucketApps(49))
        assertEquals("50-150", DeviceProvenance.bucketApps(150))
        assertEquals(">150", DeviceProvenance.bucketApps(151))
        assertNull(DeviceProvenance.bucketApps(null))
        assertEquals("0-20%", DeviceProvenance.bucketPct(0))
        assertEquals("80-100%", DeviceProvenance.bucketPct(99))
    }

    /** Canned answers for the handful of adb calls provenance makes; unknown calls answer empty. */
    private class ScriptedAdb : Adb() {
        private val answers = mapOf(
            "shell getprop ro.product.model" to "Pixel 3",
            "shell getprop ro.build.fingerprint" to
                "google/blueline/blueline:12/SP1A.210812.016.C1/8618562:user/release-keys",
            "shell getprop ro.build.version.sdk" to "31",
            "shell getprop ro.build.version.security_patch" to "2022-02-05",
            "shell getprop ro.build.display.id" to "SP1A.210812.016.C1",
            "shell uname -r" to "4.9.270",
            "shell getprop ro.soc.model" to "SDM845",
            "shell pm list packages -3" to "package:com.a\npackage:com.b\n",
            "shell df /data" to
                "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-8 57000000 19000000 38000000 34% /data\n",
            "shell dumpsys battery" to "Current Battery Service state:\n  health: 2\n  status: 2\n",
            "shell settings get global animator_duration_scale" to "0.5",
            "shell settings get global background_process_limit" to "null",
            "shell settings get global low_power" to "0",
        )

        override fun run(serial: String?, vararg args: String): Output = Output(0, answers[args.joinToString(" ")] ?: "", "")
    }
}
