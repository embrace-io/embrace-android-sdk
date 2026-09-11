package io.embrace.analysis.records.store

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.device.DeviceProfile
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure drift detector and declaration/coverage builders. [ReferenceSetTool.check]
 * must report each declared device as unchanged, drifted (by exactly its changed fields), newly
 * attached, or missing - and a genuinely drifted device must never come back "unchanged".
 * [ReferenceSetTool.declare] and [ReferenceSetTool.coverageWarnings] are pure JSON builders exercised
 * directly on already-probed profiles; no adb or device is involved anywhere in this file.
 */
class ReferenceSetToolTest {

    @Test
    fun `check reports each device as ok, drifted by its changed fields, new, or missing`() {
        val doc = JsonObject(
            mapOf(
                "devices" to JsonObject(
                    linkedMapOf(
                        "mid-a" to deviceEntry(MID_A_SERIAL, "mid", midAProfile),
                        "flagship-a" to deviceEntry(FLAGSHIP_A_SERIAL, "flagship", flagshipAProfile),
                        "mid-b" to deviceEntry(MID_B_SERIAL, "mid", midAProfile),
                        "entry-a" to deviceEntry(ENTRY_A_SERIAL, "entry", entryAProfile, retired = true),
                    ),
                ),
            ),
        )
        val probed = mapOf(
            MID_A_SERIAL to midAProfile,
            FLAGSHIP_A_SERIAL to flagshipAProfile.copy(apiLevel = 34, release = "14"),
            NEW_SERIAL to newDeviceProfile,
        )

        val lines = ReferenceSetTool.check(doc, probed)

        assertEquals(
            listOf(
                "ok 'mid-a' ($MID_A_SERIAL): profile unchanged",
                "PROFILE DRIFT on 'flagship-a' ($FLAGSHIP_A_SERIAL):",
                "    api_level: 33 -> 34",
                "    release: '13' -> '14'",
                "    -> treat this as a NEW device_key and re-establish its baseline; do " +
                    "NOT carry the old baseline across (see references/store.md).",
                "NEW device attached (not in the reference set): $NEW_SERIAL -> " +
                    "{'api_level': 30, 'release': '11', 'vendor': 'OnePlus', 'soc_family': 'Snapdragon', " +
                    "'clusters': [1500000, 2400000], 'ram_class': 'mid', 'storage_class': 'ufs-class'}",
                "MISSING 'mid-b' ($MID_B_SERIAL): attach it or mark it retired - a silent gap in the series is where slow regressions hide",
            ),
            lines,
        )
    }

    @Test
    fun `check never reports a device unchanged when every profile field has drifted`() {
        val doc =
            JsonObject(mapOf("devices" to JsonObject(mapOf("mid-a" to deviceEntry(MID_A_SERIAL, "mid", midAProfile)))))
        val fullyDrifted = DeviceProfile(
            apiLevel = 99,
            release = "99",
            vendor = "Nobody",
            socFamily = "Nothing",
            clusters = listOf(1L),
            ramClass = "unknown",
            storageClass = "unknown",
        )

        val lines = ReferenceSetTool.check(doc, mapOf(MID_A_SERIAL to fullyDrifted))

        assertTrue(lines.none { it.contains("profile unchanged") })
        assertEquals(
            listOf(
                "PROFILE DRIFT on 'mid-a' ($MID_A_SERIAL):",
                "    api_level: 31 -> 99",
                "    release: '12' -> '99'",
                "    vendor: 'Google' -> 'Nobody'",
                "    soc_family: 'Qualcomm' -> 'Nothing'",
                "    clusters: [1800000, 2800000] -> [1]",
                "    ram_class: 'mid' -> 'unknown'",
                "    storage_class: 'ufs-class' -> 'unknown'",
                "    -> treat this as a NEW device_key and re-establish its baseline; do " +
                    "NOT carry the old baseline across (see references/store.md).",
            ),
            lines,
        )
    }

    @Test
    fun `check never reports a drifted device as new or missing either - drift wins over an unmapped-looking entry`() {
        // A device whose serial IS known must be reported as drift, not folded into the NEW or
        // MISSING branches, even though its profile no longer resembles what was declared.
        val doc =
            JsonObject(mapOf("devices" to JsonObject(mapOf("mid-a" to deviceEntry(MID_A_SERIAL, "mid", midAProfile)))))

        val lines = ReferenceSetTool.check(doc, mapOf(MID_A_SERIAL to midAProfile.copy(vendor = "Someone Else")))

        assertTrue(lines.none { it.startsWith("NEW device") })
        assertTrue(lines.none { it.startsWith("MISSING") })
        assertTrue(lines.first().startsWith("PROFILE DRIFT on 'mid-a'"))
    }

    @Test
    fun `declare assigns provisional keys by a running index across ALL devices, not a per-tier letter counter`() {
        val probed = linkedMapOf(
            "S-0001" to midAProfile, // tier "mid"
            "S-0002" to entryAProfile, // tier "entry"
            "S-0003" to midAProfile, // tier "mid" again
        )

        val (doc, lines) = ReferenceSetTool.declare(probed, "2026-09-05T00:00:00")

        val devices = doc.getValue("devices").jsonObject
        // Surprising: the letter is a GLOBAL index over serial-sorted order, not a counter scoped to
        // the tier. A "mid" device that is not adjacent to the other "mid" device in serial order gets
        // a non-contiguous letter ("mid-a" then "mid-c"), which reads as if a "mid-b" exists somewhere.
        assertEquals(listOf("mid-a", "entry-b", "mid-c"), devices.keys.toList())
        assertEquals(deviceEntry("S-0001", "mid", midAProfile), devices.getValue("mid-a"))
        assertEquals(deviceEntry("S-0002", "entry", entryAProfile), devices.getValue("entry-b"))
        assertEquals(deviceEntry("S-0003", "mid", midAProfile), devices.getValue("mid-c"))
        assertEquals(JsonPrimitive("2026-09-05T00:00:00"), doc.getValue("declared_at"))
        assertEquals(
            JsonObject(
                mapOf(
                    "run_shape" to JsonObject(mapOf("passes" to JsonPrimitive(10), "iterations" to JsonPrimitive(20))),
                    "build_type" to JsonPrimitive("benchmark"),
                    "compile_state" to JsonPrimitive("profile"),
                    "instrument" to JsonNull,
                    "_comment" to JsonPrimitive(
                        "EVERY value above is provisional output of --probe, not just the device keys: " +
                            "review the recipe field by field before the first ingest. Changing any of these " +
                            "later starts a NEW comparable series; see references/store.md",
                    ),
                ),
            ),
            doc.getValue("recipe"),
        )
        assertEquals(
            listOf(
                "mid-a: S-0001 api=31 vendor=Google soc=Qualcomm ram=mid tier~mid",
                "entry-b: S-0002 api=30 vendor=Google soc=MediaTek ram=<=2GB tier~entry",
                "mid-c: S-0003 api=31 vendor=Google soc=Qualcomm ram=mid tier~mid",
                // declare() runs its own coverageWarnings on the freshly declared set: these three
                // devices all share vendor "Google", so the gap surfaces immediately at declare time.
                "COVERAGE GAP: one vendor: cannot separate OEM policy (install-time compilation, " +
                    "thermal governors, procfs readability) from silicon",
            ),
            lines,
        )
    }

    @Test
    fun `coverageWarnings flags a single device on every axis it cannot separate`() {
        val single = JsonObject(mapOf("only" to deviceEntry(MID_A_SERIAL, "mid", midAProfile)))

        assertEquals(
            listOf(
                "single device: cannot separate SDK effects from device-class effects",
                "one ART/Android generation: compile-state and class-load findings may not transfer to other releases",
                "one vendor: cannot separate OEM policy (install-time compilation, thermal governors, procfs readability) from silicon",
                "no entry/low-RAM device: the outlier classes that hurt users most (memory pressure, " +
                    "GC competition) will be under-represented",
            ),
            ReferenceSetTool.coverageWarnings(single),
        )
    }

    @Test
    fun `coverageWarnings stays silent once two devices differ in API, vendor and tier`() {
        val diverse = JsonObject(
            mapOf(
                "mid-a" to deviceEntry(MID_A_SERIAL, "mid", midAProfile),
                "entry-a" to deviceEntry(ENTRY_A_SERIAL, "entry", entryAProfile.copy(vendor = "Nokia")),
            ),
        )

        assertEquals(emptyList<String>(), ReferenceSetTool.coverageWarnings(diverse))
    }

    @Test
    fun `coverageWarnings flags a device below API 29 as unprofileable, independent of the other axes`() {
        val devices = JsonObject(
            mapOf(
                "mid-a" to deviceEntry(MID_A_SERIAL, "mid", midAProfile),
                "entry-a" to deviceEntry(ENTRY_A_SERIAL, "entry", entryAProfile.copy(vendor = "Nokia", apiLevel = 28)),
            ),
        )

        assertEquals(
            listOf("a device below API 29 cannot be traced as a profileable non-debuggable target; its numbers are not comparable"),
            ReferenceSetTool.coverageWarnings(devices),
        )
    }

    private fun deviceEntry(serial: String, tier: String, profile: DeviceProfile, retired: Boolean = false): JsonObject =
        JsonObject(
            mapOf(
                "serial" to JsonPrimitive(serial),
                "tier" to JsonPrimitive(tier),
                "profile" to StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile),
                "cool_gate_c" to JsonPrimitive(ReferenceSetTool.DEFAULT_COOL_GATE_C),
                "retired" to JsonPrimitive(retired),
            ),
        )

    private companion object {
        const val MID_A_SERIAL = "S-MID-A"
        const val FLAGSHIP_A_SERIAL = "S-FLAG-A"
        const val MID_B_SERIAL = "S-MID-B"
        const val ENTRY_A_SERIAL = "S-ENTRY-A"
        const val NEW_SERIAL = "S-NEW-0001"

        val midAProfile = DeviceProfile(
            apiLevel = 31,
            release = "12",
            vendor = "Google",
            socFamily = "Qualcomm",
            clusters = listOf(1800000L, 2800000L),
            ramClass = "mid",
            storageClass = "ufs-class",
        )
        val flagshipAProfile = DeviceProfile(
            apiLevel = 33,
            release = "13",
            vendor = "Samsung",
            socFamily = "Exynos",
            clusters = listOf(2000000L, 2900000L, 3200000L),
            ramClass = "high",
            storageClass = "ufs-class",
        )

        // Deliberately shares vendor "Google" with midAProfile above: this is the fixture the
        // "single device" and "declare" tests rely on to demonstrate the one-vendor coverage gap;
        // tests that need a diverse fleet override vendor via .copy(vendor = ...).
        val entryAProfile = DeviceProfile(
            apiLevel = 30,
            release = "11",
            vendor = "Google",
            socFamily = "MediaTek",
            clusters = listOf(1200000L),
            ramClass = "<=2GB",
            storageClass = "emmc-class",
        )
        val newDeviceProfile = DeviceProfile(
            apiLevel = 30,
            release = "11",
            vendor = "OnePlus",
            socFamily = "Snapdragon",
            clusters = listOf(1500000L, 2400000L),
            ramClass = "mid",
            storageClass = "ufs-class",
        )
    }
}
