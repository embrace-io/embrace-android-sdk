package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.fixtures.Fixtures
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.EvidenceClass
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path

/** Turning a run's provenance back into the commands that would produce it again. */
class ReproduceTest {

    private lateinit var prov: JsonObject
    private lateinit var repo: Path
    private lateinit var serials: DeviceSerials.Serials

    @Before
    fun setUp() {
        val fixture = Fixtures.root().toPath().resolve("maxims/campaign-a")
        prov = StartupJson.parseToJsonElement(java.nio.file.Files.readString(fixture.resolve("run-metadata.json"))).jsonObject
        repo = Path.of("/repo")
        serials = DeviceSerials.Serials(mapOf("mid-a" to "FIXTURE-SERIAL-A"))
    }

    @Test
    fun `a reproducible run yields the checkout, the pin and the campaign command`() {
        val plan = Reproduce.plan(prov, "run-metadata.json", "campaign-a", serials, repo)
        val text = Reproduce.render(plan)

        assertEquals(EvidenceClass.REPRODUCIBLE, plan.evidence.klass)
        assertTrue(text, text.startsWith("reproduce campaign-a (from run-metadata.json): reproducible\n"))
        assertTrue(text, text.contains("git checkout abc123"))
        assertTrue(text, text.contains("libs.versions.toml must read: embrace = \"9.2.0\""))
        assertTrue(
            text,
            text.contains(
                "tools/startup fleet-campaign --serial FIXTURE-SERIAL-A " +
                    "--out /repo/.claude/skills/_shared/local/data/runs/campaign-a-rerun " +
                    "--passes 4 --method coldStartupBaselineProfile --iterations 20",
            ),
        )
        assertTrue(text, text.contains("maxims score /repo/.claude/skills/_shared/local/data/runs/campaign-a-rerun"))
    }

    @Test
    fun `a scrubbed provenance names the device by key and looks the serial up locally`() {
        val scrubbed = JsonObject(prov.filterKeys { it != "serial" } + ("device_key" to JsonPrimitive("mid-a")))

        val withSerial = Reproduce.render(Reproduce.plan(scrubbed, "run-metadata.json", "campaign-a", serials, repo))
        val without = Reproduce.render(Reproduce.plan(scrubbed, "run-metadata.json", "campaign-a", DeviceSerials.Serials.EMPTY, repo))

        assertTrue(withSerial, withSerial.contains("--serial FIXTURE-SERIAL-A"))
        assertTrue(without, without.contains("--serial <serial of mid-a on this machine>"))
        assertTrue(without, without.contains("note: device 'mid-a' has no serial in the local device-serial map"))
    }

    @Test
    fun `a dirty tree is refused with the reason and no commands`() {
        val dirty = JsonObject(prov + ("repo_dirty" to JsonPrimitive(true)))

        val plan = Reproduce.plan(dirty, "run-metadata.json", "campaign-a", serials, repo)
        val text = Reproduce.render(plan)

        assertEquals(EvidenceClass.IRREPRODUCIBLE, plan.evidence.klass)
        assertEquals(
            "reproduce campaign-a (from run-metadata.json): irreproducible\n" +
                "  ! built from a dirty tree\n" +
                "  the traces behind this archive cannot be produced again; its datasets are the only form this measurement takes\n",
            text,
        )
    }
}
