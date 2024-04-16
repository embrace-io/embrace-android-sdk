package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.crash.EmbraceCrashService
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeLogOrchestrator
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.payload.extensions.CrashFactory
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.utils.at
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceCrashServiceTest {

    private lateinit var embraceCrashService: EmbraceCrashService
    private lateinit var logOrchestrator: FakeLogOrchestrator
    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var metadataService: FakeMetadataService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var userService: FakeUserService
    private lateinit var eventService: FakeEventService
    private lateinit var anrService: FakeAnrService
    private lateinit var ndkService: FakeNdkService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var logger: InternalEmbraceLogger

    private lateinit var crash: Crash
    private lateinit var localJsException: JsException
    private lateinit var crashMarker: CrashFileMarker
    private val testException = RuntimeException("Test exception")
    private val fakeClock = FakeClock(1000L)

    @Before
    fun setup() {
        mockkStatic(Crash::class)
        mockkObject(CrashFactory)

        logOrchestrator = FakeLogOrchestrator()
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionPropertiesService = FakeSessionPropertiesService()
        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        deliveryService = FakeDeliveryService()
        userService = FakeUserService()
        eventService = FakeEventService()
        anrService = FakeAnrService()
        ndkService = FakeNdkService()
        preferencesService = FakePreferenceService()
        crashMarker = mockk(relaxUnitFun = true)
        logger = InternalEmbraceLogger()

        localJsException = JsException("jsException", "Error", "Error", "")
    }

    private fun setupForHandleCrash() {
        val gatingService = EmbraceGatingService(FakeConfigService(), logger)

        embraceCrashService = EmbraceCrashService(
            logOrchestrator,
            sessionOrchestrator,
            sessionPropertiesService,
            metadataService,
            sessionIdTracker,
            deliveryService,
            userService,
            eventService,
            anrService,
            ndkService,
            gatingService,
            preferencesService,
            crashMarker,
            fakeClock,
            logger
        )

        metadataService.setAppForeground()
    }

    @Test
    fun `test SessionOrchestrator and LogOrchestrator are called when handleCrash is called`() {
        crash = CrashFactory.ofThrowable(logger, testException, null, 1)
        setupForHandleCrash()

        embraceCrashService.handleCrash(testException)

        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertNotNull(deliveryService.lastSentCrash)
        assertTrue(logOrchestrator.flushCalled)
        assertNotNull(sessionOrchestrator.crashId)
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with JSException`() {
        setupForHandleCrash()
        embraceCrashService.handleCrash(testException)

        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        val lastSentCrash = deliveryService.lastSentCrash
        assertNotNull(lastSentCrash)
        assertEquals(lastSentCrash?.crash?.crashId, sessionOrchestrator.crashId)

        /*
         * Verify mainCrashHandled is true after the first execution
         * by testing that a second execution of handleCrash wont run anything
         */
        embraceCrashService.handleCrash(testException)
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertNotNull(deliveryService.lastSentCrash)
        assertSame(lastSentCrash, deliveryService.lastSentCrash)
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with unityId`() {
        crash = CrashFactory.ofThrowable(logger, testException, localJsException, 1, "Unity123")
        setupForHandleCrash()
        ndkService.lastUnityCrashId = "Unity123"

        embraceCrashService.handleCrash(testException)

        verify { CrashFactory.ofThrowable(logger, testException, localJsException, 1, "Unity123") }
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertNotNull(deliveryService.lastSentCrash)
        assertEquals(crash.crashId, sessionOrchestrator.crashId)
    }

    @Test
    fun `test handleCrash calls mark() method when capture_last_run config is enabled`() {
        crash = CrashFactory.ofThrowable(logger, testException, localJsException, 1, "Unity123")
        setupForHandleCrash()

        embraceCrashService.handleCrash(testException)

        verify(exactly = 1) { crashMarker.mark() }
    }

    @After
    fun tearDown() {
        unmockkAll()
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
