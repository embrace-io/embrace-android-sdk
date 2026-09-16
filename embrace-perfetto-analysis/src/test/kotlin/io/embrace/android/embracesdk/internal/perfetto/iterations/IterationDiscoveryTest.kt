package io.embrace.android.embracesdk.internal.perfetto.iterations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

internal class IterationDiscoveryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the manifest names the run's traces, in the order it recorded them`() {
        traces(ITER_0, ITER_1, ITER_2)
        manifest(benchmark(SESSION_CLASS, SESSION_METHOD, ITER_0, ITER_1, ITER_2))

        val iterations = discoverIterations(tmp.root)
        assertEquals(listOf(0, 1, 2), iterations.map(IterationTrace::index))
        assertEquals(listOf(ITER_0, ITER_1, ITER_2), iterations.map { it.file.name })
        assertTrue(iterations.all { it.benchmark == SESSION })
        assertEquals(tmp.root, iterations.first().file.parentFile)
    }

    @Test
    fun `traces an earlier run left in the directory are not part of this one`() {
        traces(ITER_0, STALE, OTHER_BENCHMARK)
        manifest(benchmark(SESSION_CLASS, SESSION_METHOD, ITER_0))
        assertEquals(listOf(ITER_0), discoverIterations(tmp.root).map { it.file.name })
    }

    @Test
    fun `a run of several benchmarks keeps each one's iterations together and counted from zero`() {
        traces(ITER_0, ITER_1, OTHER_BENCHMARK)
        manifest(
            benchmark(SESSION_CLASS, SESSION_METHOD, ITER_0, ITER_1),
            benchmark(FIXTURE_CLASS, FIXTURE_METHOD, OTHER_BENCHMARK),
        )
        val iterations = discoverIterations(tmp.root)
        assertEquals(listOf(SESSION to 0, SESSION to 1, FIXTURE to 0), iterations.map { it.benchmark to it.index })
    }

    @Test
    fun `a run missing one of its traces fails rather than measuring the rest of it`() {
        traces(ITER_0, ITER_2)
        manifest(benchmark(SESSION_CLASS, SESSION_METHOD, ITER_0, ITER_1, ITER_2))

        val exc = assertThrows(IOException::class.java) { discoverIterations(tmp.root) }
        assertTrue(exc.message, exc.message?.contains(ITER_1) == true)
    }

    @Test
    fun `a manifest that cannot be read fails rather than being ignored`() {
        traces(ITER_0)
        write("$PACKAGE-benchmarkData.json", "{\"benchmarks\":[")

        val exc = assertThrows(IOException::class.java) { discoverIterations(tmp.root) }
        assertTrue(exc.message, exc.message?.contains("benchmarkData.json") == true)
    }

    @Test
    fun `a directory with no manifest fails rather than measuring whatever traces are in it`() {
        traces(ITER_0, OTHER_BENCHMARK)
        val exc = assertThrows(IOException::class.java) { discoverIterations(tmp.root) }
        assertTrue(exc.message, exc.message?.contains("benchmarkData.json") == true)
    }

    @Test
    fun `a run that profiled no traces reports none rather than failing`() {
        manifest(benchmark(SESSION_CLASS, SESSION_METHOD))
        assertEquals(emptyList<IterationTrace>(), discoverIterations(tmp.root))
    }

    private fun traces(vararg names: String) = names.forEach { write(it, "trace") }

    private fun write(name: String, content: String): File = tmp.newFile(name).apply { writeText(content) }

    private fun benchmark(className: String, method: String, vararg traces: String): String {
        val outputs = listOf("""{"type":"MethodTracing","label":"Method Trace","filename":"method.trace"}""") +
            traces.mapIndexed { index, name ->
                """{"type":"PerfettoTrace","label":"Trace Iteration $index","filename":"$name"}"""
            }
        return """{"name":"$method","className":"$className","profilerOutputs":[${outputs.joinToString(",")}]}"""
    }

    private fun manifest(vararg benchmarks: String) =
        write("$PACKAGE-benchmarkData.json", """{"benchmarks":[${benchmarks.joinToString(",")}]}""")

    private companion object {
        const val PACKAGE = "io.embrace.android.embracesdk.macrobenchmark"
        const val SESSION_CLASS = "$PACKAGE.SessionBenchmark"
        const val SESSION_METHOD = "sessionEnd"
        const val FIXTURE_CLASS = "$PACKAGE.TraceFixtureBenchmark"
        const val FIXTURE_METHOD = "sessionEndTraceFixture"
        const val SESSION = "SessionBenchmark.sessionEnd"
        const val FIXTURE = "TraceFixtureBenchmark.sessionEndTraceFixture"
        const val ITER_0 = "SessionBenchmark_sessionEnd_iter000_2026-09-14-15-40-53.perfetto-trace"
        const val ITER_1 = "SessionBenchmark_sessionEnd_iter001_2026-09-14-15-40-53.perfetto-trace"
        const val ITER_2 = "SessionBenchmark_sessionEnd_iter002_2026-09-14-15-40-53.perfetto-trace"
        const val STALE = "SessionBenchmark_sessionEnd_iter000_2026-09-11-14-32-48.perfetto-trace"
        const val OTHER_BENCHMARK =
            "TraceFixtureBenchmark_sessionEndTraceFixture_iter000_2026-09-11-14-44-06.perfetto-trace"
    }
}
