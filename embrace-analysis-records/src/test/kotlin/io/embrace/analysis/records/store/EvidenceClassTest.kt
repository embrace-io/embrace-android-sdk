package io.embrace.analysis.records.store

import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Whether a run's traces can be produced again from its provenance. */
class EvidenceClassTest {

    @Test
    fun `a published version from a clean recorded commit is reproducible`() {
        val verdict = EvidenceClass.of("9.2.0", "abc123", repoDirty = false)
        assertEquals(EvidenceClass.REPRODUCIBLE, verdict.klass)
        assertEquals(emptyList<String>(), verdict.reasons)
    }

    @Test
    fun `an unpublished build from a clean commit is a snapshot`() {
        val verdict = EvidenceClass.of("9.3.0-SNAPSHOT", "abc123", repoDirty = null)
        assertEquals(EvidenceClass.SNAPSHOT, verdict.klass)
        assertEquals(listOf("sdk_version '9.3.0-SNAPSHOT' is not a published artifact"), verdict.reasons)
    }

    @Test
    fun `a dirty tree or a missing commit is irreproducible whatever the version`() {
        assertEquals(EvidenceClass.IRREPRODUCIBLE, EvidenceClass.of("9.2.0", "abc123", repoDirty = true).klass)
        val noHead = EvidenceClass.of("9.2.0", null, repoDirty = false)
        assertEquals(EvidenceClass.IRREPRODUCIBLE, noHead.klass)
        assertEquals(listOf("no repo_head recorded"), noHead.reasons)
    }

    @Test
    fun `from provenance, the sdk matches check wins over the pinned version and repo_dirty is read when present`() {
        val prov = StartupJson.parseToJsonElement(
            """{"sdk_version": "9.2.0", "checks": {"sdk matches": "9.3.0-SNAPSHOT"}, "repo_head": "abc123", "repo_dirty": true}""",
        ).jsonObject
        val verdict = EvidenceClass.of(prov)
        assertEquals(EvidenceClass.IRREPRODUCIBLE, verdict.klass)
        assertEquals(listOf("built from a dirty tree"), verdict.reasons)
        val clean = StartupJson.parseToJsonElement("""{"sdk_version": "9.2.0", "repo_head": "abc123"}""").jsonObject
        assertEquals(EvidenceClass.REPRODUCIBLE, EvidenceClass.of(clean).klass)
    }
}
