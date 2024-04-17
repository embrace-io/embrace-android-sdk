package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.FakeNdkService
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
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
        assertSame(lastSentCrash, logWriter.logEvents.single())
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

        assertTrue(crashMarker.isMarked())
    }
}
