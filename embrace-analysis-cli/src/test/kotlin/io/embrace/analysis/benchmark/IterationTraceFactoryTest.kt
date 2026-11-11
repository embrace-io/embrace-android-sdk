package io.embrace.analysis.benchmark

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths

internal class IterationTraceFactoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var factory: IterationTraceFactory

    @Before
    fun setUp() {
        factory = IterationTraceFactory()
    }

    @Test
    fun `a trace's index and short name come from its iterNNN`() {
        val path = Paths.get("/x/StartupBenchmarks_coldStartup_iter007_2026-11-09-16-26-30.perfetto-trace")

        assertEquals(IterationTrace(path = path, index = 7, shortName = "iter007_2026-11-09-16-26-30"), factory.create(path))
    }

    @Test
    fun `a trace without an iterNNN has no index and keeps its whole name`() {
        val path = Paths.get("/x/manual.perfetto-trace")

        assertEquals(IterationTrace(path = path, index = null, shortName = "manual.perfetto-trace"), factory.create(path))
    }

    @Test
    fun `the traces in a directory list in iteration order, unnumbered ones last, other files left out`() {
        listOf("a_iter010_x", "a_iter002_x", "manual", "a_iter001_x").forEach { tmp.newFile("$it.perfetto-trace") }
        tmp.newFile("notes.txt")

        val names = factory.listIn(tmp.root.toPath()).map { it.path.fileName.toString() }

        assertEquals(
            listOf("a_iter001_x", "a_iter002_x", "a_iter010_x", "manual").map { "$it.perfetto-trace" },
            names,
        )
    }
}
