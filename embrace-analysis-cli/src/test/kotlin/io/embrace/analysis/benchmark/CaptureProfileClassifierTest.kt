package io.embrace.analysis.benchmark

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

internal class CaptureProfileClassifierTest {

    private val classifier = CaptureProfileClassifier()

    @Test
    fun `each of the macrobenchmark's capture configs is classified as the profile its file is named for, and only those`() {
        val dir = File(checkNotNull(System.getProperty("analysis.captureConfigsDir")))
        val configs = checkNotNull(dir.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) })
        val classified = configs.associate { file ->
            file.name.removePrefix(PREFIX).removeSuffix(SUFFIX) to classifier.classify(file.readText()).key
        }
        classified.forEach { (key, profile) -> assertEquals(key, key, profile) }
        assertEquals(
            listOf(CaptureProfile.APP_ONLY, CaptureProfile.WITH_CONTEXT, CaptureProfile.MEMORY).map { it.key }.sorted(),
            classified.keys.sorted(),
        )
    }

    @Test
    fun `a config asking for any atrace category but dalvik is androidx-benchmark's default`() {
        assertEquals(CaptureProfile.ANDROIDX_DEFAULT, classifier.classify(ANDROIDX_DEFAULT))
        // even one that also asks for the scheduling and memory events the macrobenchmark's profiles are told apart by
        assertEquals(
            CaptureProfile.ANDROIDX_DEFAULT,
            classifier.classify(ANDROIDX_DEFAULT + "ftrace_config {\n ftrace_events: \"sched/sched_switch\"\n}\n"),
        )
    }

    @Test
    fun `scheduling makes a capture with-context, the resident set size without it memory, and neither app-only`() {
        assertEquals(CaptureProfile.WITH_CONTEXT, classifier.classify(ftrace("sched/sched_switch", "kmem/rss_stat")))
        assertEquals(CaptureProfile.MEMORY, classifier.classify(ftrace("kmem/rss_stat")))
        assertEquals(CaptureProfile.APP_ONLY, classifier.classify(ftrace("task/task_rename")))
    }

    @Test
    fun `a trace that records no config is unknown`() {
        assertEquals(CaptureProfile.UNKNOWN, classifier.classify(null))
        assertEquals(CaptureProfile.UNKNOWN, classifier.classify(""))
    }

    private fun ftrace(vararg events: String): String =
        "data_sources {\n  config {\n    ftrace_config {\n" +
            "      atrace_categories: \"dalvik\"\n" +
            events.joinToString("") { "      ftrace_events: \"$it\"\n" } +
            "    }\n  }\n}\n"

    private companion object {
        const val PREFIX = "perfetto-config-"
        const val SUFFIX = ".pbtx"

        // the part of androidx.benchmark's default config the classifier reads
        const val ANDROIDX_DEFAULT = "data_sources {\n  config {\n    ftrace_config {\n" +
            "      ftrace_events: \"kmem/rss_stat\"\n      atrace_categories: \"am\"\n      atrace_categories: \"dalvik\"\n" +
            "      atrace_categories: \"sched\"\n    }\n  }\n}\n"
    }
}
