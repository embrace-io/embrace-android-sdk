package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LeakSuspectEncoderTest {

    @Test
    fun `no suspects encodes to an empty string`() {
        assertEquals("", encodeLeakSuspects(emptyList()))
    }

    @Test
    fun `a single suspect encodes as one group with one entry`() {
        val suspects = listOf(snapshot(className = "com.example.MainActivity", cyclesSurvived = 4))

        assertEquals("part_sess:activity|com.example.MainActivity|4", encodeLeakSuspects(suspects))
    }

    @Test
    fun `suspects sharing a session part are grouped, ordered by cyclesSurvived descending`() {
        val suspects = listOf(
            snapshot(className = "First", cyclesSurvived = 2),
            snapshot(className = "Second", cyclesSurvived = 5),
        )

        assertEquals("part_sess:activity|Second|5,activity|First|2", encodeLeakSuspects(suspects))
    }

    @Test
    fun `suspects from different session parts are encoded as separate groups`() {
        val suspects = listOf(
            snapshot(sessionPartId = "part1", objectType = "activity", className = "Foo", cyclesSurvived = 10),
            snapshot(sessionPartId = "part2", objectType = "fragment", className = "Bar", cyclesSurvived = 20),
        )

        // groups appear in the order their first (highest-cyclesSurvived) member was reached
        assertEquals("part2_sess:fragment|Bar|20;part1_sess:activity|Foo|10", encodeLeakSuspects(suspects))
    }

    @Test
    fun `a suspect whose token is not a LeakContext is dropped`() {
        val suspects =
            listOf(LeakDetector.LeakSnapshot(trackedAtMs = 0L, token = "not a LeakContext", cyclesSurvived = 1, className = "Foo"))

        assertEquals("", encodeLeakSuspects(suspects))
    }

    @Test
    fun `a suspect with no known session part is dropped`() {
        assertEquals("", encodeLeakSuspects(listOf(snapshot(sessionPartId = ""))))
    }

    @Test
    fun `a suspect with no known user session is dropped`() {
        assertEquals("", encodeLeakSuspects(listOf(snapshot(userSessionId = ""))))
    }

    @Test
    fun `truncation keeps the highest cyclesSurvived and stops at the first candidate that does not fit`() {
        val suspects = listOf(
            snapshot(className = "A", cyclesSurvived = 5),
            snapshot(className = "B", cyclesSurvived = 3),
            // shorter than B - would fit in the remaining budget, but is never tried because B already didn't fit
            snapshot(objectType = "x", className = "y", cyclesSurvived = 1),
        )

        assertEquals("part_sess:activity|A|5", encodeLeakSuspects(suspects, maxLength = 28))
    }

    @Test
    fun `the encoded result never exceeds maxLength`() {
        val suspects = (1..50).map { snapshot(className = "com.example.SomeActivity$it", cyclesSurvived = it.toLong()) }

        assertTrue(encodeLeakSuspects(suspects, maxLength = 100).length <= 100)
    }

    private fun snapshot(
        sessionPartId: String = "part",
        userSessionId: String = "sess",
        objectType: String = "activity",
        className: String = "com.example.MainActivity",
        cyclesSurvived: Long = 0L,
    ): LeakDetector.LeakSnapshot = LeakDetector.LeakSnapshot(
        trackedAtMs = 0L,
        token = LeakContext(objectType, SessionIdsSnapshot(userSessionId = userSessionId, sessionPartId = sessionPartId)),
        cyclesSurvived = cyclesSurvived,
        className = className,
    )
}
