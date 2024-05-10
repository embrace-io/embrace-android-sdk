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
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
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
    private lateinit var logger: EmbLogger
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
        logger = EmbLoggerImpl()
        localJsException = JsException(
            "NullPointerException",
            "Null pointer exception occurred",
            "RuntimeException",
            "at com.example.MyClass.method(MyClass.kt:10)"
        )
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

    @Test
    fun `test RN crash by calling logUnhandledJsException() before handleCrash()`() {
        setupForHandleCrash()
        crashDataSource.logUnhandledJsException(localJsException)
        crashDataSource.handleCrash(testException)

        val lastSentCrashAttributes = logWriter.logEvents.single().schemaType.attributes()
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertEquals(1, logWriter.logEvents.size)
        assertEquals(lastSentCrashAttributes["log.record.uid"], sessionOrchestrator.crashId)
        assertEquals(lastSentCrashAttributes["emb.type"], "sys.android.react_native_crash")
        assertEquals(
            "{\"n\":\"NullPointerException\",\"" +
                "m\":\"Null pointer exception occurred\",\"" +
                "t\":\"RuntimeException\",\"" +
                "st\":\"" +
                "at com.example.MyClass.method(MyClass.kt:10)\"}",
            lastSentCrashAttributes["emb.android.react_native_crash.js_exception"]
        )
    }
}
