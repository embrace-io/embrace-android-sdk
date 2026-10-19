package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.benchmark.fakes.FakeEmbraceSpan
import io.embrace.android.embracesdk.benchmark.scenario.RecordingScope.Companion.START_TIME_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Runs each dead part against a fake SDK, as [PersistenceScenariosTest] does the persistence scenarios.
 */
internal class ResurrectionScenariosTest {

    @Test
    fun `the small part is a short session`() {
        assertDeadPart(
            ResurrectionScenarios.smallPart,
            id = "small_part",
            Contents(requests = 10, breadcrumbs = 5, completed = 5, inFlight = 1, properties = 2),
        )
    }

    @Test
    fun `the medium part is a longer session`() {
        assertDeadPart(
            ResurrectionScenarios.mediumPart,
            id = "medium_part",
            Contents(requests = 100, breadcrumbs = 25, completed = 40, inFlight = 5, properties = 5),
        )
    }

    @Test
    fun `the large part is a long session`() {
        assertDeadPart(
            ResurrectionScenarios.largePart,
            id = "large_part",
            Contents(requests = 500, breadcrumbs = 60, completed = 150, inFlight = 15, properties = 20),
        )
    }

    @Test
    fun `the capped part fills the sdk's default limits per session part and goes over none`() {
        assertDeadPart(
            ResurrectionScenarios.cappedPart,
            id = "capped_part",
            Contents(requests = 2000, breadcrumbs = 100, completed = 450, inFlight = 50, properties = 100),
        )
        val scope = run(ResurrectionScenarios.cappedPart)
        assertEquals(500, scope.embrace.spans.size)
        val perDomain = scope.embrace.networkRequests.groupingBy { URI(it.url).host }.eachCount()
        assertTrue("$perDomain", perDomain.values.all { it <= 1000 })
    }

    @Test
    fun `the spans still running when the app instance dies are uploads started at its end`() {
        val uploads = run(ResurrectionScenarios.largePart).embrace.spans.filter(FakeEmbraceSpan::isRecording)

        assertEquals(15, uploads.size)
        uploads.forEach { upload ->
            assertEquals("upload-photo", upload.name)
            assertEquals(START_TIME_MS, upload.startTimeMs)
            assertEquals(5, upload.attributes.size)
            assertEquals(listOf(FakeEmbraceSpan.Event("upload-started", START_TIME_MS, emptyMap())), upload.events)
        }
    }

    @Test
    fun `an unknown id fails loudly`() {
        assertThrows(IllegalStateException::class.java) { ResurrectionScenarios.byId("huge_part") }
    }

    /**
     * Checks that [scenario] is defined as the dead part called [id] holding [contents]: it is found by its id,
     * has a description, and records its part without waiting, its requests and spans having just ended after
     * durations of their own.
     */
    private fun assertDeadPart(scenario: ScenarioSpec, id: String, contents: Contents) {
        assertEquals(id, scenario.id)
        assertSame(scenario, ResurrectionScenarios.byId(id))
        assertTrue("$id has no description", scenario.description.isNotBlank())

        val scope = run(scenario)
        assertEquals(contents, contentsOf(scope))

        // a device runs it in real time, so it must not wait
        assertEquals(0, scope.elapsedMs)
        val requests = scope.embrace.networkRequests
        assertTrue(requests.all { it.endTime == START_TIME_MS })
        assertDistinctDurations(requests.map { it.endTime - it.startTime })
        val completed = scope.embrace.spans.filter { it.endTimeMs != null }
        assertTrue(completed.all { it.endTimeMs == START_TIME_MS && it.events.size == 1 })
        assertDistinctDurations(completed.map { checkNotNull(it.endTimeMs) - checkNotNull(it.startTimeMs) })
    }

    private fun run(scenario: ScenarioSpec): RecordingScope = RecordingScope().also { scenario.action(it) }

    private fun contentsOf(scope: RecordingScope): Contents = Contents(
        requests = scope.embrace.networkRequests.size,
        breadcrumbs = scope.embrace.breadcrumbs.size,
        completed = scope.embrace.spans.count { it.endTimeMs != null },
        inFlight = scope.embrace.spans.count(FakeEmbraceSpan::isRecording),
        properties = scope.embrace.userSessionProperties.size,
    )

    // durations repeat only past 211 items, so that a part's numbers vary as a real app's do
    private fun assertDistinctDurations(durations: List<Long>) {
        assertTrue("$durations", durations.all { it in 40L..250L })
        assertEquals(minOf(durations.size, 211), durations.distinct().size)
    }

    private data class Contents(
        val requests: Int,
        val breadcrumbs: Int,
        val completed: Int,
        val inFlight: Int,
        val properties: Int,
    )
}
