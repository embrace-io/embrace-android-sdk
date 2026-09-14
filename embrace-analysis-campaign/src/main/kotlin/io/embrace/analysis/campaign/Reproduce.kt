package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.records.Provenance
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.EvidenceClass
import io.embrace.analysis.records.store.RecordsRoot
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * The parameters that produced a run, turned back into the commands that would produce it again. This is
 * what makes "the datasets are the evidence, the traces are re-derivable" literal: a campaign's provenance
 * pins the app commit, the SDK coordinate, the device key, the method and the shape, and pinning those
 * re-creates the population the run sampled. What it cannot pin - a dirty tree, an unrecorded commit - it
 * refuses to pretend about, and says why.
 */
object Reproduce {

    data class Plan(
        val runId: String,
        val source: String,
        val evidence: EvidenceClass.Verdict,
        val steps: List<String>,
        val notes: List<String>,
    )

    /** The plan for a run directory or an unpacked archive; null when it carries no provenance. */
    fun plan(runDir: Path, runId: String, serials: DeviceSerials.Serials, repo: Path): Plan? {
        val (prov, source) = Provenance.load(runDir) ?: return null
        return plan(prov, source, runId, serials, repo)
    }

    fun plan(prov: JsonObject, source: String, runId: String, serials: DeviceSerials.Serials, repo: Path): Plan {
        val steps = ArrayList<String>()
        val notes = ArrayList<String>()
        steps.addAll(checkoutSteps(prov))
        val out = RecordsRoot.localRuns(repo).resolve("$runId-rerun")
        if (source == "cell-state.json") {
            steps.add(cellStep(prov, out, notes))
        } else {
            steps.add(campaignStep(prov, serialArgument(prov, serials, notes), out, notes))
        }
        steps.add("tools/startup maxims score $out --reference-set ${RecordsRoot.REL}/longitudinal/reference-set.json --ledger none")
        return Plan(runId, source, EvidenceClass.of(prov), steps, notes)
    }

    fun render(plan: Plan): String {
        val sb = StringBuilder()
        sb.append("reproduce ${plan.runId} (from ${plan.source}): ${plan.evidence.klass}\n")
        plan.evidence.reasons.forEach { sb.append("  ! $it\n") }
        if (plan.evidence.klass == EvidenceClass.IRREPRODUCIBLE) {
            sb.append("  the traces behind this archive cannot be produced again; its datasets are the only form this measurement takes\n")
            return sb.toString()
        }
        if (plan.evidence.klass == EvidenceClass.SNAPSHOT) {
            sb.append("  an unpublished build: reproducible only while the commit above still publishes the same artifact\n")
        }
        plan.steps.forEach { sb.append("  $it\n") }
        plan.notes.forEach { sb.append("  note: $it\n") }
        return sb.toString()
    }

    /** The commit to check out and the SDK coordinate the app must pin, as far as the provenance records them. */
    private fun checkoutSteps(prov: JsonObject): List<String> {
        val steps = ArrayList<String>()
        PyJson.strOrNull(prov, "repo_head")?.let {
            steps.add("git checkout $it   # the app and harness exactly as they were built")
        }
        val pin = PyJson.strOrNull(prov, "catalog_pin")
        val sdk = PyJson.strOrNull(PyJson.obj(prov, "checks"), "sdk matches") ?: PyJson.strOrNull(prov, "sdk_version")
        if (pin != null) {
            steps.add("# examples/ExampleApp/gradle/libs.versions.toml must read: $pin")
        } else if (sdk != null) {
            steps.add("# pin the ExampleApp's SDK coordinate to $sdk")
        }
        return steps
    }

    /** The serial to drive: from the provenance itself (a run recorded before the split) or the local map by device key. */
    private fun serialArgument(prov: JsonObject, serials: DeviceSerials.Serials, notes: MutableList<String>): String {
        val deviceKey = PyJson.strOrNull(prov, "device_key")
        val serial = PyJson.strOrNull(prov, "serial") ?: deviceKey?.let { serials.serialFor(it) }
        if (serial != null) return serial
        if (deviceKey == null) return "<serial>"
        notes.add("device '$deviceKey' has no serial in the local device-serial map; attach the handset and add it")
        return "<serial of $deviceKey on this machine>"
    }

    private fun cellStep(prov: JsonObject, out: Path, notes: MutableList<String>): String {
        val cell = PyJson.obj(prov, "cell")
        val levels = PyJson.obj(cell, "levels") ?: JsonObject(emptyMap())
        notes.add("the matrix plan file is not archived; the cell's levels were ${PyJson.dumps(levels)}")
        return "tools/startup cell-runner --cells <plan.json> --cell ${PyJson.strOrNull(cell, "id") ?: "<cell id>"} --out $out"
    }

    private fun campaignStep(prov: JsonObject, serialArg: String, out: Path, notes: MutableList<String>): String {
        val shape = PyJson.obj(prov, "run_shape")
        val iterations = PyJson.double(shape, "iterations")?.toInt()
        val cmd = StringBuilder("tools/startup fleet-campaign --serial $serialArg --out $out")
        PyJson.double(shape, "passes")?.toInt()?.let { cmd.append(" --passes $it") }
        PyJson.strOrNull(prov, "method")?.let { cmd.append(" --method $it") }
        iterations?.let { cmd.append(" --iterations $it") }
        if (iterations == null) {
            notes.add("the run did not record an iteration count; the benchmark file's value at that commit applies")
        }
        return cmd.toString()
    }
}
