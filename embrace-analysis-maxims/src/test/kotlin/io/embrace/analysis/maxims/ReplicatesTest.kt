package io.embrace.analysis.maxims

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Between-campaign spread and verdict stability over the cells measured more than once. */
class ReplicatesTest {

    private lateinit var root: FixtureRecords.Root

    @Before
    fun setUp() {
        root = FixtureRecords.build()
    }

    @Test
    fun `the replicated cell is reported with both replicates, its spreads and stable verdicts`() {
        val report = Replicates.report(root.records, root.serials)

        val lines = report.lines()
        assertEquals("replicates: 1 cell(s) measured more than once, of 2", lines[0])
        assertTrue(report, report.contains("mid-a / 9.2.0 / profile / coldStartupBaselineProfile / 4x20  (2 replicates)"))
        assertTrue(report, report.contains("campaign-a "))
        assertTrue(report, report.contains("campaign-a-again "))
        assertTrue("two identical replicates have zero spread", report.contains("between campaigns: median SD 0.00 ms"))
        assertTrue(report, report.contains("within a campaign: pass-median SE "))
        assertTrue("identical datasets score identically", report.contains("verdicts: every maxim reads the same on all replicates"))
        assertTrue(report, report.contains("read with care"))
    }
}
