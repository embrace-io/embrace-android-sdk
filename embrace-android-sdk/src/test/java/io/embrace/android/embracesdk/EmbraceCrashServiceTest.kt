package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.crash.EmbraceCrashService
import io.embrace.android.embracesdk.capture.crash.EmbraceUncaughtExceptionHandler
import io.embrace.android.embracesdk.config.local.CrashHandlerLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.ExceptionInfo
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.payload.extensions.CrashFactory
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService
import io.embrace.android.embracesdk.utils.at
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class EmbraceCrashServiceTest {

    private lateinit var embraceCrashService: EmbraceCrashService
    private lateinit var sessionService: FakeSessionService
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var metadataService: FakeMetadataService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var userService: FakeUserService
    private lateinit var eventService: FakeEventService
    private lateinit var anrService: FakeAnrService
    private lateinit var ndkService: FakeNdkService
    private lateinit var configService: FakeConfigService
    private lateinit var preferencesService: FakePreferenceService

    private lateinit var crash: Crash
    private lateinit var localJsException: JsException
    private lateinit var crashMarker: CrashFileMarker
    private val testException = RuntimeException("Test exception")
    private val fakeClock = FakeClock(1000L)

    @Before
    fun setup() {
        mockkStatic(Crash::class)
        mockkObject(CrashFactory)

        sessionService = FakeSessionService()
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

        localJsException = JsException("jsException", "Error", "Error", "")
        crash = CrashFactory.ofThrowable(testException, localJsException, 1)
    }

    private fun setupForHandleCrash(crashHandlerEnabled: Boolean) {
        configService = FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = {
                    LocalConfig(
                        "",
                        false,
                        SdkLocalConfig(crashHandler = CrashHandlerLocalConfig(crashHandlerEnabled))
                    )
                }
            )
        )

        val gatingService = EmbraceGatingService(FakeConfigService())

        embraceCrashService = EmbraceCrashService(
            configService,
            sessionService,
            sessionPropertiesService,
            metadataService,
            sessionIdTracker,
            deliveryService,
            userService,
            eventService,
            anrService,
            ndkService,
            gatingService,
            null,
            preferencesService,
            crashMarker,
            fakeClock
        )

        metadataService.setAppForeground()
        every { CrashFactory.ofThrowable(any(), any(), any(), any()) } returns crash
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with JSException`() {
        setupForHandleCrash(true)
        embraceCrashService.handleCrash(Thread.currentThread(), testException)

        verify { CrashFactory.ofThrowable(testException, localJsException, any(), any()) }
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        val lastSentCrash = deliveryService.lastSentCrash
        assertNotNull(lastSentCrash)
        assertEquals(crash.crashId, sessionService.crashId)

        /*
        * Verify mainCrashHandled is true after the first execution
        * by testing that a second execution of handleCrash wont run anything
        */
        embraceCrashService.handleCrash(Thread.currentThread(), testException)
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertNotNull(deliveryService.lastSentCrash)
        assertSame(lastSentCrash, deliveryService.lastSentCrash)
        assertEquals(crash.crashId, sessionService.crashId)
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with unityId`() {
        crash = CrashFactory.ofThrowable(testException, localJsException, 1, "Unity123")
        setupForHandleCrash(false)
        ndkService.lastUnityCrashId = "Unity123"

        embraceCrashService.handleCrash(Thread.currentThread(), testException)

        verify { CrashFactory.ofThrowable(testException, localJsException, 1, "Unity123") }
        assertEquals(1, anrService.forceAnrTrackingStopOnCrashCount)
        assertNotNull(deliveryService.lastSentCrash)
        assertEquals(crash.crashId, sessionService.crashId)
    }

    @Test
    fun `test handleCrash calls mark() method when capture_last_run config is enabled`() {
        crash = CrashFactory.ofThrowable(testException, localJsException, 1, "Unity123")
        setupForHandleCrash(false)

        embraceCrashService.handleCrash(Thread.currentThread(), testException)

        verify(exactly = 1) { crashMarker.mark() }
    }

    @Test
    fun `test exception handler is registered with config option enabled`() {
        setupForHandleCrash(true)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
    }

    @Test
    fun `test exception handler is not registered with config option disabled`() {
        setupForHandleCrash(false)
        assert(Thread.getDefaultUncaughtExceptionHandler() !is EmbraceUncaughtExceptionHandler)
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
                ExceptionInfo(
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
