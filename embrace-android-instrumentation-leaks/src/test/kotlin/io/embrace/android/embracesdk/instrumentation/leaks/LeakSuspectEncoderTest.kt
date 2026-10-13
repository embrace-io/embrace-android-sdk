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
    fun `a single suspect encodes as one group with one entry, id rendered as hex`() {
        val suspects = listOf(snapshot(reportedClass = String::class.java, cyclesSurvived = 4, id = 255))

        assertEquals("part_sess:activity|java.lang.String|4|ff", encodeLeakSuspects(suspects))
    }

    @Test
    fun `a negative id is encoded as its unsigned hex form, not a minus sign`() {
        val suspects = listOf(snapshot(id = -1))

        assertEquals("part_sess:activity|java.lang.String|0|ffffffff", encodeLeakSuspects(suspects))
    }

    @Test
    fun `suspects sharing a session part are grouped, ordered by cyclesSurvived descending`() {
        val suspects = listOf(
            snapshot(reportedClass = Thread::class.java, cyclesSurvived = 2, id = 1),
            snapshot(reportedClass = StringBuilder::class.java, cyclesSurvived = 5, id = 2),
        )

        assertEquals(
            "part_sess:activity|java.lang.StringBuilder|5|2,activity|java.lang.Thread|2|1",
            encodeLeakSuspects(suspects),
        )
    }

    @Test
    fun `two distinct instances of the same class in the same group are both included, told apart by id`() {
        val suspects = listOf(
            snapshot(reportedClass = String::class.java, cyclesSurvived = 3, id = 1),
            snapshot(reportedClass = String::class.java, cyclesSurvived = 3, id = 2),
        )

        assertEquals(
            "part_sess:activity|java.lang.String|3|1,activity|java.lang.String|3|2",
            encodeLeakSuspects(suspects),
        )
    }

    @Test
    fun `suspects from different session parts are encoded as separate groups`() {
        val suspects = listOf(
            snapshot(sessionPartId = "part1", objectType = "activity", reportedClass = String::class.java, cyclesSurvived = 10, id = 1),
            snapshot(sessionPartId = "part2", objectType = "fragment", reportedClass = Thread::class.java, cyclesSurvived = 20, id = 2),
        )

        // groups appear in the order their first (highest-cyclesSurvived) member was reached
        assertEquals(
            "part2_sess:fragment|java.lang.Thread|20|2;part1_sess:activity|java.lang.String|10|1",
            encodeLeakSuspects(suspects),
        )
    }

    @Test
    fun `a suspect whose token is not a LeakContext is dropped`() {
        val suspects = listOf(
            LeakDetector.LeakSnapshot(trackedAtMs = 0L, token = "not a LeakContext", cyclesSurvived = 1, id = 0),
        )

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
            // renders to 39 characters on its own
            snapshot(reportedClass = String::class.java, cyclesSurvived = 5),
            // would take the total to 69
            snapshot(reportedClass = Thread::class.java, cyclesSurvived = 3),
            // would take the total to only 64 - fits the budget, but is never tried because the one above didn't
            snapshot(objectType = "x", reportedClass = Runnable::class.java, cyclesSurvived = 1),
        )

        assertEquals("part_sess:activity|java.lang.String|5|0", encodeLeakSuspects(suspects, maxLength = 66))
    }

    @Test
    fun `the encoded result never exceeds maxLength`() {
        val suspects = (1..50).map {
            snapshot(cyclesSurvived = it.toLong(), id = it)
        }

        assertTrue(encodeLeakSuspects(suspects, maxLength = 100).length <= 100)
    }

    private fun snapshot(
        sessionPartId: String = "part",
        userSessionId: String = "sess",
        objectType: String = "activity",
        reportedClass: Class<*> = String::class.java,
        cyclesSurvived: Long = 0L,
        id: Int = 0,
    ): LeakDetector.LeakSnapshot = LeakDetector.LeakSnapshot(
        trackedAtMs = 0L,
        token = LeakContext(
            objectType,
            SessionIdsSnapshot(userSessionId = userSessionId, sessionPartId = sessionPartId),
            reportedClass,
        ),
        cyclesSurvived = cyclesSurvived,
        id = id,
    )
}
