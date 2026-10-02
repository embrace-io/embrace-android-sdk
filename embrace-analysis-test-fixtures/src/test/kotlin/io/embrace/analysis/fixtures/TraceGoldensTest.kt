package io.embrace.analysis.fixtures

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * [TraceGoldens] as a caller that only wants the frozen CSVs sees it: the archived set is present and
 * every golden has its `startup_metrics.csv`, and the live-trace lookup returns null rather than
 * fabricating a path when neither the env var nor the default directory point at anything.
 */
class TraceGoldensTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `all is non-empty and every golden has a startup_metrics csv`() {
        val goldens = TraceGoldens.all()

        assertTrue("expected at least one trace golden from data.zip", goldens.isNotEmpty())
        goldens.forEach { golden ->
            assertTrue(
                "${golden.device}/${golden.stem} missing startup_metrics.csv",
                Files.isRegularFile(golden.dir.resolve("startup_metrics.csv")),
            )
        }
    }

    @Test
    fun `traceFile is null when the env var is unset and the default directory is absent`() {
        assumeTrue(
            "STARTUP_TOOLS_TRACE_FIXTURES is set in this environment; the default-absent case cannot be exercised here",
            System.getenv("STARTUP_TOOLS_TRACE_FIXTURES") == null,
        )
        val golden = TraceGoldens.all().first()
        val repoRootWithNoTraceFixtures = tempFolder.newFolder("repo-without-traces").toPath()

        val trace = TraceGoldens.traceFile(golden, repoRootWithNoTraceFixtures)

        assertNull(trace)
    }
}
