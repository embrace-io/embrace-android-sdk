package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
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
    fun `a confirmed suspect is encoded into the memory leak suspects session attribute at session part end`() {
        val leaked = Any()
        confirmLeak(leaked, sessionIds = SESSION_IDS)

        dataSource.onPreSessionEnd()

        val encoded = args.destination.attributes[EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS]
        assertTrue(
            "expected the part_session group key, object type and class name; the exact cycle count isn't asserted here" +
                " since it depends on the real GC-cycle counter - see LeakSuspectEncoderTest for that math",
            encoded?.startsWith("part-1_session-1:activity|${leaked.javaClass.name}|") == true,
        )
    }

    @Test
    fun `nothing is reported when there are no confirmed suspects`() {
        dataSource.onPreSessionEnd()

        assertFalse(args.destination.attributes.containsKey(EmbSessionAttributes.EMB_MEMORY_LEAK_SUSPECTS))
    }

    @Test
    fun `an object that has only outlived one sentinel is not reported`() {
        val leaked = Any()
        val detector = dataSource.leakDetector
        detector.trackOpened(leaked)
        val ref = checkNotNull(detector.trackClosed(leaked, LeakContext(OBJECT_TYPE, SESSION_IDS)))

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
        detector.trackOpened(referent)
        val ref = checkNotNull(detector.trackClosed(referent, LeakContext(OBJECT_TYPE, sessionIds)))

        val confirmation = checkNotNull(detector.onSentinelReclaimed(ref))
        detector.onSentinelReclaimed(confirmation)
    }

    private companion object {
        const val OBJECT_TYPE = "activity"
        val SESSION_IDS = SessionIdsSnapshot(userSessionId = "session-1", sessionPartId = "part-1")
    }
}
