package io.embrace.android.embracesdk.benchmark.capture

/**
 * The instrumentation argument that names the [CaptureProfile] a run uses.
 */
const val CAPTURE_PROFILE_ARGUMENT: String = "embrace.capture"

/**
 * The perfetto configs a benchmark can capture with. Which one a run uses is chosen outside the code,
 * through the [CAPTURE_PROFILE_ARGUMENT] instrumentation argument (`scripts/run-macrobenchmark.sh --capture`),
 * so the same benchmark can capture either way without a rebuild; [captureProfileWithKey] turns that argument
 * into a profile. The profile is part of what makes two runs comparable, and the trace records the config it
 * was captured with, so the analysis can tell which one it is reading.
 *
 * @param key what `--capture` calls the profile: its identity outside this code, in scripts, campaigns
 * and recorded runs, so it is pinned here rather than derived from the constant's name.
 */
enum class CaptureProfile(val key: String, private val resource: String) {

    /** The app's own sections and counters, and nothing else. ~120 KB an iteration. */
    APP_ONLY("app-only", "/perfetto-config-app-only.pbtx"),

    /** The sections plus the scheduling, frequency, binder, runtime and memory context around them. */
    WITH_CONTEXT("with-context", "/perfetto-config-with-context.pbtx"),

    /**
     * The sections plus what `MemoryUsageMetric` reads: the app's resident set size (`kmem/rss_stat`)
     * and ART's heap size counter (the `dalvik` category), without the scheduling `with-context` adds.
     */
    MEMORY("memory", "/perfetto-config-memory.pbtx"),
    ;

    /** The config's text, as androidx.benchmark takes it, read from the resource. */
    val traceConfig: String
        get() = checkNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" }
            .use { it.reader().readText() }
}

/**
 * The profile [key] names, or [CaptureProfile.APP_ONLY] when there is no key.
 */
fun captureProfileWithKey(key: String?): CaptureProfile {
    if (key == null) {
        return CaptureProfile.APP_ONLY
    }
    return checkNotNull(CaptureProfile.entries.firstOrNull { it.key == key }) {
        "unknown $CAPTURE_PROFILE_ARGUMENT '$key'; one of ${CaptureProfile.entries.joinToString { it.key }}"
    }
}
