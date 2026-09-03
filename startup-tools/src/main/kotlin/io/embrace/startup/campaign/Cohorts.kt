package io.embrace.startup.campaign

import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * `_shared/cohorts.py`: per-iteration user-session cohort verification for a benchmark pass.
 *
 * `start-first-session` runs one of two paths on every cold start: RESTORE the persisted user session
 * (within its inactivity timeout; near-zero cost) or CREATE and PERSIST a new one (no stored session,
 * inactive, or expired; the production cold-start path). An arm that intends one path can silently take
 * the other and the numbers still look valid, so the campaign runner does not trust its setup: it
 * enables the ExampleApp's logcat telemetry tap, streams the `EmbVerify` lines for the pass, and this
 * classifies every launch from its exported `sdk-init` span. Output text is identical to the Python's.
 */
object Cohorts {

    const val SETTING_KEY: String = "embrace_verify_telemetry"
    const val SETTING_VALUE: String = "startup:1"
    val LOGCAT_ARGS: List<String> = listOf("logcat", "-v", "threadtime", "-T", "1", "-s", "EmbVerify:I")

    private val ATTRS = listOf(
        "emb.user_session_id",
        "emb.app.version_startup_counter",
        "start-first-session-duration-ms",
        "post-init-duration-ms",
        "init-compile-filter",
        "thread-name",
        "ended-in-foreground",
    )

    // threadtime: "09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 2 1/1 {json chunk}"
    private val LINE = Regex("""^\S+ \S+\s+(\d+)\s+(\d+)\s+I EmbVerify: EMBV1 (\d+) (\d+)/(\d+) (.*)$""")

    /** One launch = one complete `emb-sdk-init` span; `attrs` holds the [ATTRS] subset (null when absent). */
    data class Launch(
        val pid: Long,
        val attrs: Map<String, String?>,
        val initMs: Double?,
        var iteration: Int = 0,
        var cohort: String = "unknown",
    ) {
        val sessionId: String? get() = attrs["emb.user_session_id"]
        val counter: String? get() = attrs["emb.app.version_startup_counter"]
    }

    private class Pending(val total: Int) {
        val parts = HashMap<Int, String>()
    }

    /** Launch records in log order; chunks of one payload share (pid, seq) and are joined in index order. */
    fun parseEmbVerify(text: String): List<Launch> {
        val pending = LinkedHashMap<Pair<String, String>, Pending>()
        text.lineSequence().forEach { line ->
            val m = LINE.matchEntire(line) ?: return@forEach
            val g = m.groupValues // 1 pid, 2 tid, 3 seq, 4 chunk index, 5 chunk count, 6 chunk
            val entry = pending.getOrPut(g[1] to g[3]) { Pending(g[5].toInt()) }
            entry.parts[g[4].toInt()] = g[6]
        }
        val launches = ArrayList<Launch>()
        pending.forEach { (key, entry) ->
            if (entry.parts.size != entry.total) {
                return@forEach
            }
            val payload = entry.parts.keys.sorted().joinToString("") { entry.parts.getValue(it) }
            val record = runCatching { StartupJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return@forEach
            if (str(record, "kind") != "span" || str(record, "name") != "emb-sdk-init") {
                return@forEach
            }
            val attrs = (record["attrs"] as? JsonObject) ?: JsonObject(emptyMap())
            val start = (record["startNanos"] as? JsonPrimitive)?.longOrNull
            val end = (record["endNanos"] as? JsonPrimitive)?.longOrNull
            launches.add(
                Launch(
                    pid = key.first.toLong(),
                    attrs = ATTRS.associateWith { str(attrs, it) },
                    initMs = if (start != null && end != null) (end - start) / 1e6 else null,
                ),
            )
        }
        return launches
    }

    /** Adds `iteration` and `cohort` to each launch, in place, and returns the list. */
    fun classify(launches: List<Launch>): List<Launch> {
        var prevId: String? = null
        launches.forEachIndexed { i, launch ->
            launch.iteration = i
            val session = launch.sessionId
            launch.cohort = when {
                i == 0 -> if (launch.counter == "1") "created" else "unknown"
                session != null && session == prevId -> "restored"
                else -> "created"
            }
            prevId = session
        }
        return launches
    }

    /** The cohort a StartupBenchmarks method intends, or null when the method is unknown. */
    fun expectedCohort(method: String?): String? = when {
        method.isNullOrEmpty() -> null
        "NewUserSession" in method || "ExpiredUserSession" in method -> "created"
        else -> "restored"
    }

    /** Iterations whose cohort contradicts [expected]; a restoring arm's first launch is a fresh install and exempt. */
    fun violations(launches: List<Launch>, expected: String?): List<Int> {
        if (expected == null) {
            return emptyList()
        }
        return launches.filter { launch ->
            launch.cohort != "unknown" && launch.cohort != expected && !(expected == "restored" && launch.iteration == 0)
        }.map { it.iteration }
    }

    fun summarize(launches: List<Launch>, expected: String?, bad: List<Int>): String {
        val created = launches.count { it.cohort == "created" }
        val restored = launches.count { it.cohort == "restored" }
        val unknown = launches.count { it.cohort == "unknown" }
        var line = "cohorts: ${launches.size} launches, $created created, $restored restored, $unknown unknown; " +
            "expected ${expected ?: "n/a"}; violations: ${bad.size}"
        if (bad.isNotEmpty()) {
            line += " (iterations " + bad.joinToString(", ") + ")"
        }
        return line
    }

    fun launchLine(launch: Launch): String {
        val session = (launch.sessionId ?: "?").take(SESSION_PREFIX)
        return "  iter ${launch.iteration.toString().padStart(3, '0')}  pid ${launch.pid}  ${launch.cohort.padEnd(8)} " +
            "session $session  counter ${launch.counter ?: "?"}  " +
            "start-first-session ${launch.attrs["start-first-session-duration-ms"] ?: "?"} ms  " +
            "post-init ${launch.attrs["post-init-duration-ms"] ?: "?"} ms  " +
            "compile ${launch.attrs["init-compile-filter"] ?: "?"}"
    }

    data class Report(val launches: List<Launch>, val expected: String?, val violations: List<Int>, val summary: String)

    /** Parse + classify + check, exactly as the Python's `report()`. */
    fun report(text: String, method: String?): Report {
        val launches = classify(parseEmbVerify(text))
        val expected = expectedCohort(method)
        val bad = violations(launches, expected)
        return Report(launches, expected, bad, summarize(launches, expected, bad))
    }

    /** The `passN-cohorts.json` tree; same keys as the Python's (its `sort_keys` ordering is not reproduced). */
    fun toJson(report: Report, method: String?): JsonObject = JsonObject(
        mapOf(
            "method" to prim(method),
            "expected" to prim(report.expected),
            "violations" to JsonArray(report.violations.map { JsonPrimitive(it) }),
            "launches" to JsonArray(report.launches.map { launchJson(it) }),
        ),
    )

    private fun launchJson(launch: Launch): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        fields["pid"] = JsonPrimitive(launch.pid)
        launch.attrs.forEach { (k, v) -> fields[k] = prim(v) }
        fields["init_ms"] = launch.initMs?.let { JsonPrimitive(it) } ?: JsonNull
        fields["iteration"] = JsonPrimitive(launch.iteration)
        fields["cohort"] = JsonPrimitive(launch.cohort)
        return JsonObject(fields)
    }

    private fun prim(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull

    private fun str(obj: JsonObject, key: String): String? = (obj[key] as? JsonPrimitive)?.jsonPrimitive?.content

    private const val SESSION_PREFIX = 8
}
