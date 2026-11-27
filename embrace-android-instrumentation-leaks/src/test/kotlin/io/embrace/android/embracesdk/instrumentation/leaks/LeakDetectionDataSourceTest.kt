package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class LeakDetectionDataSourceTest {

    // a real counter has counted collections by the time anything is confirmed, since confirming takes two
    private var gcCycleCount: Long = 2L

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: LeakDetectionDataSource

    @Before
    fun setUp() {
        gcCycleCount = 2L
        args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        val detector = LeakDetector(args.clock, args.errorHandler, args.versionChecker) { gcCycleCount.toString() }
        dataSource = LeakDetectionDataSource(args, detector)

        // nothing is tracked unless the detector is running, and this is how it is started in production
        dataSource.onDataCaptureEnabled()
    }

    @After
    fun tearDown() {
        dataSource.leakDetector.stop()
    }

    @Test
    fun `a confirmed suspect is reported with the cycles it survived since the previous session part`() {
        val leaked = Any()
        val closedAtMs = args.clock.now()
        confirmLeak(leaked, sessionIds = SESSION_IDS)
        args.clock.tick(5000)

        dataSource.onPreSessionEnd()
        assertEquals(
            "the confirming collection counts as one",
            "2;part-1_session-1:activity|java.lang.Object|1",
            encoded(),
        )
        assertEquals("${closedAtMs + 5000};0;part-1_session-1:activity|java.lang.Object|5000", candidates())

        args.destination.attributes.clear()
        gcCycleCount += 3
        dataSource.onPreSessionEnd()
        assertEquals("2;part-1_session-1:activity|java.lang.Object|3", encoded())
        assertNull("its close time was already sent", candidates())

        args.destination.attributes.clear()
        dataSource.onPreSessionEnd()
        assertNull("no collection since the previous part", encoded())
        assertNull(candidates())

        dataSource.leakDetector.trackOpened(leaked, OWNER)
        gcCycleCount += 2
        dataSource.onPreSessionEnd()
        assertNull("a reopened suspect is no longer reported", encoded())
    }

    private fun encoded(): String? = args.destination.attributes[EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS]

    private fun candidates(): String? = args.destination.attributes[EmbSessionAttributes.EMB_MEMORY_LEAK_CANDIDATES]

    @Test
    fun `nothing is reported when there are no confirmed suspects`() {
        dataSource.onPreSessionEnd()

        assertFalse(args.destination.attributes.containsKey(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS))
    }

    @Test
    fun `an object that has only outlived one sentinel is not reported`() {
        val leaked = Any()
        val detector = dataSource.leakDetector
        detector.trackOpened(leaked, OWNER)
        val ref = checkNotNull(
            detector.trackClosed(leaked, OWNER, LeakContext(OBJECT_TYPE, SESSION_IDS, leaked.javaClass)),
        )

        detector.onSentinelReclaimed(ref)
        dataSource.onPreSessionEnd()

        assertFalse(
            "a suspected leak is not reported until it is confirmed",
            args.destination.attributes.containsKey(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS),
        )
    }

    @Test
    fun `a suspect whose session is unknown is not reported`() {
        confirmLeak(Any(), sessionIds = SessionIdsSnapshot("", ""))

        dataSource.onPreSessionEnd()

        assertFalse(args.destination.attributes.containsKey(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS))
    }

    /**
     * Drives both reclamations the detector thread would otherwise drive, confirming the suspect.
     */
    private fun confirmLeak(referent: Any, sessionIds: SessionIdsSnapshot) {
        val detector = dataSource.leakDetector
        detector.trackOpened(referent, OWNER)
        val ref = checkNotNull(
            detector.trackClosed(referent, OWNER, LeakContext(OBJECT_TYPE, sessionIds, referent.javaClass)),
        )

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        detector.onSentinelReclaimed(confirmation)
    }

    private companion object {
        const val OBJECT_TYPE = "activity"
        val OWNER = TrackingSource.ACTIVITY
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}
