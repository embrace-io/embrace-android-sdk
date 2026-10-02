package io.embrace.analysis.records.store

import io.embrace.analysis.common.json.PyJson
import kotlinx.serialization.json.JsonObject

/**
 * Whether the traces behind a campaign could be produced again from the parameters its provenance
 * records. The datasets in the archive are the evidence; this says what the evidence can be checked
 * against if someone is unsatisfied with it.
 *
 * - [REPRODUCIBLE]: a published SDK version built from a clean tree at a recorded commit. Pinning the
 *   version, the app commit, the device key and the recipe re-creates the population the run sampled;
 *   only machine time stands between the record and fresh traces.
 * - [SNAPSHOT]: a clean tree at a recorded commit, but an unpublished SDK build. Reproducible only while
 *   that commit exists and publishes the same artefact; treated as comparison-only by the store.
 * - [IRREPRODUCIBLE]: a dirty tree, or no recorded commit. The archive is the only form this
 *   measurement will ever take.
 */
object EvidenceClass {

    const val REPRODUCIBLE: String = "reproducible"
    const val SNAPSHOT: String = "snapshot"
    const val IRREPRODUCIBLE: String = "irreproducible"

    data class Verdict(val klass: String, val reasons: List<String>)

    fun of(sdkVersion: String?, repoHead: String?, repoDirty: Boolean?): Verdict {
        val reasons = ArrayList<String>()
        if (repoHead.isNullOrEmpty()) reasons.add("no repo_head recorded")
        if (repoDirty == true) reasons.add("built from a dirty tree")
        if (reasons.isNotEmpty()) return Verdict(IRREPRODUCIBLE, reasons)
        if (!Ingest.isPublished(sdkVersion)) {
            return Verdict(SNAPSHOT, listOf("sdk_version ${sdkVersion?.let { PyJson.repr(it) } ?: "None"} is not a published artifact"))
        }
        return Verdict(REPRODUCIBLE, emptyList())
    }

    /** From a provenance document (`run-metadata.json` or `cell-state.json`), using the resolved SDK when the caller has one. */
    fun of(prov: JsonObject?, sdkVersion: String? = null): Verdict =
        of(
            sdkVersion ?: PyJson.strOrNull(PyJson.obj(prov, "checks"), "sdk matches") ?: PyJson.strOrNull(prov, "sdk_version"),
            PyJson.strOrNull(prov, "repo_head"),
            prov?.get("repo_dirty")?.let { PyJson.bool(prov, "repo_dirty", false) },
        )
}
