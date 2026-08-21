package io.embrace.android.embracesdk.instrumentation.leaks

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.semconv.EmbMemoryLeakAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class LeakDetectionDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: LeakDetectionDataSource

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(RuntimeEnvironment.getApplication())
        dataSource = LeakDetectionDataSource(args)

        // nothing is tracked unless the detector is running, and this is how it is started in production
        dataSource.onDataCaptureEnabled()
    }

    @After
    fun tearDown() {
        dataSource.onDataCaptureDisabled()
    }

    @Test
    fun `a confirmed leak is reported as a back-dated log carrying the leak attributes`() {
        // held strongly for the duration of the test so that it cannot be collected
        val leaked = Any()
        val lifecycleEndedAtMs = args.clock.now()

        detectLeak(leaked, sessionIds = SESSION_IDS, detectionDelayMs = DETECTION_DELAY_MS)

        val log = args.destination.logEvents.single()
        assertEquals(LogSeverity.WARNING, log.severity)
        assertEquals("Leaked activity: ${leaked.javaClass.name}", log.message)
        assertEquals(EmbType.System.MemoryLeak, log.schemaType.telemetryType)
        assertEquals("memory-leak", log.schemaType.fixedObjectName)

        assertEquals(
            "the log belongs at the end of the lifecycle, not at the point the leak was confirmed",
            lifecycleEndedAtMs,
            log.timestampMs,
        )

        val attributes = log.schemaType.attributes()
        assertEquals(OBJECT_TYPE, attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_OBJECT_TYPE])
        assertEquals(leaked.javaClass.name, attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_CLASS_NAME])
        assertEquals(SESSION_IDS.userSessionId, attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_USER_SESSION_ID])
        assertEquals(SESSION_IDS.sessionPartId, attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_SESSION_PART_ID])
        assertEquals(
            DETECTION_DELAY_MS.toString(),
            attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_DETECTION_DELAY_MS],
        )
        assertEquals(
            "the identity hash code names this instance, so that repeated reports can be matched up",
            System.identityHashCode(leaked).toString(),
            attributes[EmbMemoryLeakAttributes.MEMORY_LEAK_IDENTITY_HASH_CODE],
        )
    }

    @Test
    fun `the standard session attributes are blanked so the leak's own session is the only one named`() {
        val leaked = Any()
        detectLeak(leaked, sessionIds = SESSION_IDS, detectionDelayMs = DETECTION_DELAY_MS)

        val attributes = args.destination.logEvents.single().schemaType.attributes()
        assertEquals("", attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals("", attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
    }

    @Test
    fun `an object that has only outlived one sentinel is not reported`() {
        val leaked = Any()
        val detector = dataSource.leakDetector
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked, LeakContext(OBJECT_TYPE, SESSION_IDS)))

        detector.onSentinelReclaimed(ref)

        assertTrue("a suspected leak is not reported until it is confirmed", args.destination.logEvents.isEmpty())
    }

    @Test
    fun `a leak whose session is unknown is not reported`() {
        val leaked = Any()

        detectLeak(leaked, sessionIds = SessionIdsSnapshot("", ""), detectionDelayMs = DETECTION_DELAY_MS)

        assertTrue(
            "nothing names the session this leak belongs to, so it cannot be attributed to one",
            args.destination.logEvents.isEmpty(),
        )
    }

    @Test
    fun `a leak with only a session part and no user session is not reported`() {
        val leaked = Any()

        detectLeak(leaked, sessionIds = SessionIdsSnapshot("", "part-1"), detectionDelayMs = DETECTION_DELAY_MS)

        assertTrue(args.destination.logEvents.isEmpty())
    }

    @Test
    fun `a leak tracked without a LeakContext is not reported`() {
        val leaked = Any()
        val detector = dataSource.leakDetector
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked, token = "not a leak context"))

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        detector.onSentinelReclaimed(confirmation)

        assertTrue("without a context there is no session or object type to report", args.destination.logEvents.isEmpty())
    }

    /**
     * Drives the reclamations the detector thread would otherwise drive, advancing the clock so that the reported detection
     * delay is the given one.
     */
    private fun detectLeak(referent: Any, sessionIds: SessionIdsSnapshot, detectionDelayMs: Long) {
        val detector = dataSource.leakDetector
        detector.trackOpened(referent)
        val ref = checkNotNull(detector.trackClosed(referent, LeakContext(OBJECT_TYPE, sessionIds)))

        args.clock.tick(detectionDelayMs)

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        detector.onSentinelReclaimed(confirmation)
    }

    private companion object {
        const val OBJECT_TYPE = "activity"
        const val DETECTION_DELAY_MS = 4000L
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}
