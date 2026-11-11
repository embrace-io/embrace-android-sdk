package io.embrace.analysis.startup

import androidx.benchmark.traceprocessor.rowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

internal class StartupMetricsReaderTest {

    private val reader = StartupMetricsReader()

    @Test
    fun `sections, window and TTID are read in ms, sections in the order the query returned them`() {
        val metrics = reader.fromRows(
            sections = sequenceOf(
                rowOf("name" to "emb-modules-init", "dur" to 7_261_963L),
                rowOf("name" to "emb-config-service-init", "dur" to 2_882_853L),
            ),
            window = rowOf("dur" to 14_411_743L, "src" to "emb-sdk-start", "wait" to 1_339_682L, "running" to 12_172_403L, "cpus" to 2L),
            ttid = rowOf("dur" to 247_247_030L),
        )

        assertEquals(listOf("emb-modules-init", "emb-config-service-init"), metrics.sections.keys.toList())
        assertEquals(7.261963, metrics.sections.getValue("emb-modules-init"), 0.0)
        assertEquals(
            StartupWindow(
                durationMs = 14.411743,
                source = StartupWindow.Source.SDK_START,
                initThread = StartupWindow.ThreadScheduling(runningMs = 12.172403, waitMs = 1.339682, cpus = 2),
            ),
            metrics.window,
        )
        assertEquals(247.24703, metrics.ttidMs!!, 0.0)
    }

    @Test
    fun `a composed window is read as such`() {
        val metrics = reader.fromRows(
            sections = emptySequence(),
            window = rowOf("dur" to 1_000_000L, "src" to "composed", "wait" to 0L, "running" to 0L, "cpus" to 0L),
            ttid = null,
        )
        assertEquals(StartupWindow.Source.COMPOSED, metrics.window?.source)
    }

    @Test
    fun `a trace without a window or TTID has neither, rather than zeros`() {
        val metrics = reader.fromRows(
            sections = sequenceOf(rowOf("name" to "emb-modules-init", "dur" to 1_000_000L)),
            window = null,
            ttid = null,
        )
        assertNull(metrics.window)
        assertNull(metrics.ttidMs)
        assertEquals(mapOf("emb-modules-init" to 1.0), metrics.sections)
    }

    @Test
    fun `a window source the query doesn't produce is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            reader.fromRows(
                sections = emptySequence(),
                window = rowOf("dur" to 1L, "src" to "guessed", "wait" to 0L, "running" to 0L, "cpus" to 0L),
                ttid = null,
            )
        }
    }
}
