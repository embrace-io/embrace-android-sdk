package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.instrumentation.leaks.LeakDetector.LeakSnapshot
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LeakSuspectEncoderTest {

    @Test
    fun `nothing to report encodes as empty, but a dropped count alone still encodes a candidates header`() {
        assertEquals("", encodeLeakSuspects(emptyList()))
        assertEquals("", encodeLeakCandidates(emptyList(), BASE_MS, dropped = 0))
        assertEquals("$BASE_MS;3", encodeLeakCandidates(emptyList(), BASE_MS, dropped = 3))
    }

    @Test
    fun `suspects are summed per class and grouped by session part, most cycles first`() {
        val suspects = listOf(
            snapshot("part-1", String::class.java, cycles = 3),
            snapshot("part-2", Thread::class.java, cycles = 7, objectType = "fragment"),
            snapshot("part-1", StringBuilder::class.java, cycles = 1, objectType = "fragment_view"),
            snapshot("part-1", String::class.java, cycles = 2),
        )

        assertEquals(
            "2;part-2_session-1:fragment|java.lang.Thread|7;" +
                "part-1_session-1:activity|java.lang.String|5,fragment_view|java.lang.StringBuilder|1",
            encodeLeakSuspects(suspects),
        )
    }

    @Test
    fun `a suspect with no token or no session is left out of both attributes`() {
        val suspects = listOf(
            LeakSnapshot(trackedAtMs = 0L, token = null, cyclesSurvived = 1, isNew = true),
            snapshot("", String::class.java, cycles = 1),
        )

        assertEquals("", encodeLeakSuspects(suspects))
        assertEquals("", encodeLeakCandidates(suspects, BASE_MS, dropped = 0))
    }

    @Test
    fun `rows that don't fit are left out while later shorter ones are still written, within the limit`() {
        val suspects = listOf(
            snapshot("part-1", LeakSuspectEncoderTest::class.java, cycles = 9),
            snapshot("part-1", String::class.java, cycles = 1),
        )
        assertEquals("2;part-1_session-1:activity|java.lang.String|1", encodeLeakSuspects(suspects, maxLength = 50))

        val crowdedSuspects = (1..64).map { snapshot("part-$it", String::class.java, cycles = it.toLong()) }
        val crowded = encodeLeakSuspects(crowdedSuspects)
        assertTrue(crowded.length <= 2000)
        assertTrue("not every row fits", crowded.count { it == ';' } < 64)

        val closeTimes = List(3) { snapshot("part-1", String::class.java, closedAgoMs = 10_000L * (it + 1)) }
        assertEquals(
            "the two oldest close times fit, the newest doesn't",
            "$BASE_MS;0;part-1_session-1:activity|java.lang.String|30000.20000",
            encodeLeakCandidates(closeTimes, BASE_MS, dropped = 0, maxLength = 72),
        )
    }

    @Test
    fun `candidates list only new suspects' close times, oldest first, after the base and dropped count`() {
        val suspects = listOf(
            snapshot("part-1", String::class.java, closedAgoMs = 41_200),
            snapshot("part-2", Thread::class.java, closedAgoMs = 77_000, objectType = "fragment"),
            snapshot("part-1", String::class.java, closedAgoMs = 90_000),
            snapshot("part-1", String::class.java, closedAgoMs = 5_000, isNew = false),
        )

        assertEquals(
            "$BASE_MS;2;part-1_session-1:activity|java.lang.String|90000.41200;" +
                "part-2_session-1:fragment|java.lang.Thread|77000",
            encodeLeakCandidates(suspects, BASE_MS, dropped = 2),
        )
    }

    private fun snapshot(
        sessionPartId: String,
        reportedClass: Class<*>,
        cycles: Long = 1,
        closedAgoMs: Long = 0,
        isNew: Boolean = true,
        objectType: String = "activity",
    ): LeakSnapshot = LeakSnapshot(
        trackedAtMs = BASE_MS - closedAgoMs,
        token = LeakContext(
            objectType,
            SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = sessionPartId),
            reportedClass,
        ),
        cyclesSurvived = cycles,
        isNew = isNew,
    )

    private companion object {
        const val BASE_MS = 1_795_000_000_000L
    }
}
