package io.embrace.analysis.benchmark

/**
 * Tells which [CaptureProfile] a trace was captured with from the config it records, as trace_processor gives it
 * (`metadata.trace_config_pbtxt`, the TraceConfig as text). It goes by what the config asks for rather than matching
 * it whole, so a profile whose config is retuned keeps its name: the macrobenchmark's configs ask for no atrace category
 * but `dalvik`, so one that asks for any other is androidx.benchmark's default; of the macrobenchmark's, `with-context`
 * alone asks for scheduling, and `memory` alone for the resident set size.
 */
class CaptureProfileClassifier {

    /** The profile [configText] describes, or [CaptureProfile.UNKNOWN] when the trace records none. */
    fun classify(configText: String?): CaptureProfile {
        if (configText.isNullOrBlank()) {
            return CaptureProfile.UNKNOWN
        }
        val ftraceEvents = values(configText, "ftrace_events")
        val atraceCategories = values(configText, "atrace_categories")
        return when {
            (atraceCategories - MACROBENCHMARK_CATEGORIES).isNotEmpty() -> CaptureProfile.ANDROIDX_DEFAULT
            SCHED_SWITCH in ftraceEvents -> CaptureProfile.WITH_CONTEXT
            RSS_STAT in ftraceEvents -> CaptureProfile.MEMORY
            else -> CaptureProfile.APP_ONLY
        }
    }

    /** Every value of the repeated string [field] in [configText], across all its data sources. */
    private fun values(configText: String, field: String): Set<String> =
        Regex("""\b$field:\s*"([^"]*)"""").findAll(configText).map { it.groupValues[1] }.toSet()

    private companion object {
        const val SCHED_SWITCH = "sched/sched_switch"
        const val RSS_STAT = "kmem/rss_stat"

        /** The atrace categories the macrobenchmark's configs ask for. */
        val MACROBENCHMARK_CATEGORIES = setOf("dalvik")
    }
}
