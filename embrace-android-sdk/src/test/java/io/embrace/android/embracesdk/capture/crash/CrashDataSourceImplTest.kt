package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCrashFileMarker
import io.embrace.android.embracesdk.fakes.FakeLogOrchestrator
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.utils.at
import io.mockk.verify
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class CrashDataSourceImplTest {

    private lateinit var crashDataSource: CrashDataSourceImpl
    private lateinit var logOrchestrator: FakeLogOrchestrator
    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var anrService: FakeAnrService
    private lateinit var ndkService: FakeNdkService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var crashMarker: FakeCrashFileMarker
    private lateinit var configService: FakeConfigService
    private lateinit var serializer: EmbraceSerializer
    private lateinit var logWriter: FakeLogWriter
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var localJsException: JsException
    private lateinit var testException: Exception
    @Before
    fun setUp() {
        logOrchestrator = FakeLogOrchestrator()
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionProperties = fakeEmbraceSessionProperties()
        anrService = FakeAnrService()
        ndkService = FakeNdkService()
        preferencesService = FakePreferenceService()
        crashMarker = FakeCrashFileMarker()
        logWriter = FakeLogWriter()
        configService = FakeConfigService()
        serializer = EmbraceSerializer()
        logger = InternalEmbraceLogger()
        localJsException = JsException("jsException", "Error", "Error", "")
        testException = RuntimeException("Test exception")
    }

    private fun setupForHandleCrash() {
        crashDataSource = CrashDataSourceImpl(
            logOrchestrator,
            sessionOrchestrator,
            sessionProperties,
            anrService,
            ndkService,
            preferencesService,
            crashMarker,
            logWriter,
            configService,
            serializer,
            logger
        )
    }

    @Test
    fun `test SessionOrchestrator and LogOrchestrator are called when handleCrash is called`() {
        setupForHandleCrash()

        crashDataSource.handleCrash(testException)

        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertEquals(1, logWriter.logEvents.size)
        assertTrue(logOrchestrator.flushCalled)
        assertNotNull(sessionOrchestrator.crashId)
    }

    @Test
    fun `test LogWriter and SessionOrchestrator are called when handleCrash is called with JSException`() {
        setupForHandleCrash()
        crashDataSource.handleCrash(testException)

        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertEquals(1, logWriter.logEvents.size)
        val lastSentCrash = logWriter.logEvents.single()
        assertEquals(logWriter.logEvents.single().schemaType.attributes()["log.record.uid"], sessionOrchestrator.crashId)

        /*
         * Verify mainCrashHandled is true after the first execution
         * by testing that a second execution of handleCrash wont run anything
         */
        crashDataSource.handleCrash(testException)
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertEquals(1, logWriter.logEvents.size)
        Assert.assertSame(lastSentCrash, logWriter.logEvents.single())
    }

    @Test
    fun `test LogWriter and SessionOrchestrator are called when handleCrash is called with unityId`() {
        setupForHandleCrash()
        ndkService.lastUnityCrashId = "Unity123"

        crashDataSource.handleCrash(testException)

        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertEquals(1, logWriter.logEvents.size)
        assertEquals(logWriter.logEvents.single().schemaType.attributes()["log.record.uid"], sessionOrchestrator.crashId)
        assertEquals(ndkService.lastUnityCrashId, sessionOrchestrator.crashId)
    }

    @Test
    fun `test handleCrash calls mark() method when capture_last_run config is enabled`() {
        setupForHandleCrash()

        crashDataSource.handleCrash(testException)

        verify(exactly = 1) { crashMarker.mark() }
    }

    @Test
    fun testSerialization() {
        val crash = Crash(
            "123",
            listOf(
                LegacyExceptionInfo(
                    "java.lang.RuntimeException",
                    "ExceptionMessage",
                    listOf("stacktrace.line")
                )
            ),
            listOf("js_exception"),
            listOf(
                ThreadInfo(
                    123,
                    Thread.State.RUNNABLE,
                    "ReferenceHandler",
                    1,
                    listOf("stacktrace.line.thread")
                )
            )
        )
        assertJsonMatchesGoldenFile("crash_expected.json", crash)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Crash>("crash_expected.json")
        assertEquals("123", obj.crashId)
        assertEquals("java.lang.RuntimeException", obj.exceptions?.at(0)?.name)
        assertEquals("ExceptionMessage", obj.exceptions?.at(0)?.message)
        assertEquals("stacktrace.line", obj.exceptions?.at(0)?.lines?.at(0))
        assertEquals("js_exception", obj.jsExceptions?.at(0))
        assertEquals(123L, obj.threads?.at(0)?.threadId)
        assertEquals(Thread.State.RUNNABLE, obj.threads?.at(0)?.state)
        assertEquals("ReferenceHandler", obj.threads?.at(0)?.name)
        assertEquals(1, obj.threads?.at(0)?.priority)
        assertEquals("stacktrace.line.thread", obj.threads?.at(0)?.lines?.at(0))
    }
}
