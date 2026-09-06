package io.embrace.startup.analysis

import io.embrace.startup.core.json.SchemaRoundTripTest
import io.embrace.startup.perfetto.TraceGoldens
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The three campaign reports against the frozen golden stdout - first on the synthetic
 * campaigns captured for the fixtures (three passes, injected outliers, a missing pass, a
 * dropped section, a truncated campaign log), then on the real one-pass fixture campaigns when the
 * trace goldens are present. Text is compared line for line after trimming trailing whitespace.
 */
class CampaignReportsTest {

    private val goldens: Path = SchemaRoundTripTest.fixturesRoot().toPath().resolve("goldens")
    private val campaignA = goldens.resolve("inputs/campaign-a")
    private val campaignB = goldens.resolve("inputs/campaign-b")

    @Test
    fun `hypothesis tests reproduce on the synthetic three-pass campaign, temps included`() {
        val passes = Campaign.variancePasses(campaignA)
        assertEquals(3, passes.size)
        val temps = HypothesisTests.parseTemps(campaignA.resolve("campaign.log"))
        assertEquals(HypothesisTests.Temps(28.5, 30.1), temps[1])
        assertEquals(HypothesisTests.Temps(29.5, null), temps[3])
        assertText(Files.readString(goldens.resolve("hypothesis_tests.stdout.txt")), HypothesisTests.report(passes, temps))
    }

    @Test
    fun `factors report reproduces on the synthetic campaign, including the None paths`() {
        val passes = Campaign.factorsPasses(campaignA)
        assertEquals(3, passes.size)
        assertText(Files.readString(goldens.resolve("factors_report.stdout.txt")), FactorsReport.report(passes))
    }

    @Test
    fun `cross-device sections pool repeated labels and skip a missing pass`() {
        val data = linkedMapOf(
            "synth-mid" to Campaign.pooledVariance(listOf(campaignA, campaignB)),
            "synth-entry" to Campaign.pooledVariance(listOf(campaignB)),
        )
        assertEquals(30, data.getValue("synth-mid").size)
        assertEquals(12, data.getValue("synth-entry").size)
        assertText(Files.readString(goldens.resolve("cross_device_sections.stdout.txt")), CrossDeviceSections.report(data))
    }

    @Test
    fun `the same three reports reproduce on the real one-pass fixture campaigns`() {
        val campaigns = TraceGoldens.root().resolve("_campaign")
        assumeTrue("fixture campaigns not present", Files.isDirectory(campaigns))
        val devices = Files.list(campaigns).use { s -> s.filter { Files.isDirectory(it) }.sorted().toList() }
        var compared = 0
        devices.forEach { dir ->
            val device = dir.fileName.toString()
            TraceGoldens.cliStdout("hypothesis_tests.$device.stdout.txt")?.let { want ->
                val passes = Campaign.variancePasses(dir)
                assertText(want, HypothesisTests.report(passes, HypothesisTests.parseTemps(dir.resolve("campaign.log"))))
                compared++
            }
            TraceGoldens.cliStdout("factors_report.$device.stdout.txt")?.let { want ->
                assertText(want, FactorsReport.report(Campaign.factorsPasses(dir)))
                compared++
            }
        }
        TraceGoldens.cliStdout("cross_device_sections.all.stdout.txt")?.let { want ->
            val order = listOf("mid-b", "mid-a", "flagship-a").filter { Files.isDirectory(campaigns.resolve(it)) }
            val data = LinkedHashMap<String, List<VarianceAnalysis.Record>>()
            order.forEach { data[it] = Campaign.pooledVariance(listOf(campaigns.resolve(it))) }
            assertText(want, CrossDeviceSections.report(data))
            compared++
        }
        assumeTrue("no fixture campaign CLI goldens yet", compared > 0)
    }

    private fun assertText(want: String, got: String) {
        val w = want.lines().map { it.trimEnd() }.dropLastWhile { it.isEmpty() }
        val g = got.lines().map { it.trimEnd() }.dropLastWhile { it.isEmpty() }
        assertEquals(w.joinToString("\n"), g.joinToString("\n"))
    }
}
