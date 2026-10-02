package io.embrace.analysis.cli

import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.maxims.LinkCheck
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.RecordsRoot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The committed records root's provenance chain, checked on every test run: every archive is scored or
 * cited, every citation and ledger run has its archive, no archive carries a raw member or a serial, and
 * the evidence index is current. Failing here means `tools/startup records cull` or `records index`
 * (or a FINDINGS entry) is owed before the records are committed.
 */
class RecordsChainTest {

    @Test
    fun `the committed records root has an unbroken provenance chain`() {
        val repo = RepoRoot.locate()
        val problems = LinkCheck.run(RecordsRoot.dir(repo), DeviceSerials.load(DeviceSerials.file(repo)))

        assertEquals(problems.joinToString("\n"), emptyList<LinkCheck.Problem>(), problems)
    }
}
