package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.capture.crash.EmbraceCrashService
import io.embrace.android.embracesdk.capture.crash.EmbraceUncaughtExceptionHandler
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.CrashHandlerLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Crash
import io.embrace.android.embracesdk.payload.ExceptionInfo
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.payload.ThreadInfo
import io.embrace.android.embracesdk.session.SessionService
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
import org.junit.Before
import org.junit.Test

internal class EmbraceCrashServiceTest {

    private lateinit var embraceCrashService: EmbraceCrashService
    private lateinit var sessionService: SessionService
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var metadataService: FakeAndroidMetadataService
    private lateinit var deliveryService: EmbraceDeliveryService
    private lateinit var userService: UserService
    private lateinit var eventService: EventService
    private lateinit var anrService: AnrService
    private lateinit var ndkService: NdkService
    private lateinit var configService: ConfigService

    private lateinit var crash: Crash
    private lateinit var localJsException: JsException
    private lateinit var crashMarker: CrashFileMarker
    private val testException = RuntimeException("Test exception")
    private val fakeClock = FakeClock(1000L)

    @Before
    fun setup() {
        mockkStatic(Crash::class)
        mockkObject(Crash.Companion)

        sessionService = mockk(relaxed = true)
        sessionPropertiesService = FakeSessionPropertiesService()
        metadataService = FakeAndroidMetadataService()
        deliveryService = mockk(relaxUnitFun = true)
        userService = mockk(relaxed = true)
        eventService = mockk(relaxed = true)
        anrService = mockk(relaxUnitFun = true)
        ndkService = mockk()
        crashMarker = mockk(relaxUnitFun = true)

        localJsException = JsException("jsException", "Error", "Error", "")
        crash = Crash.ofThrowable(testException, localJsException)
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

        val gatingService = EmbraceGatingService(
            mockk(relaxed = true) {
                every { sessionBehavior } returns fakeSessionBehavior()
            }
        )

        embraceCrashService = EmbraceCrashService(
            configService,
            sessionService,
            sessionPropertiesService,
            metadataService,
            deliveryService,
            userService,
            eventService,
            anrService,
            ndkService,
            gatingService,
            null,
            crashMarker,
            fakeClock
        )

        metadataService.setAppForeground()
        every { Crash.ofThrowable(any(), any(), any()) } returns crash
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with JSException`() {
        setupForHandleCrash(true)
        every { ndkService.getUnityCrashId() } returns null
        embraceCrashService.handleCrash(Thread.currentThread(), testException)

        verify { Crash.ofThrowable(testException, localJsException, any()) }
        verify { anrService.forceAnrTrackingStopOnCrash() }

        verify { deliveryService.sendCrash(any()) }
        verify { sessionService.handleCrash(crash.crashId) }

        /*
        * Verify mainCrashHandled is true after the first execution
        * by testing that a second execution of handleCrash wont run anything
        */
        embraceCrashService.handleCrash(Thread.currentThread(), testException)
        verify(exactly = 1) { anrService.forceAnrTrackingStopOnCrash() }
        verify(exactly = 1) { deliveryService.sendCrash(any()) }
        verify(exactly = 1) { sessionService.handleCrash(crash.crashId) }
    }

    @Test
    fun `test ApiClient and SessionService are called when handleCrash is called with unityId`() {
        crash = Crash.ofThrowable(testException, localJsException, "Unity123")
        setupForHandleCrash(false)
        every { ndkService.getUnityCrashId() } returns "Unity123"

        embraceCrashService.handleCrash(Thread.currentThread(), testException)

        verify { Crash.ofThrowable(testException, localJsException, "Unity123") }
        verify { anrService.forceAnrTrackingStopOnCrash() }
        verify { deliveryService.sendCrash(any()) }
        verify { sessionService.handleCrash(crash.crashId) }
    }

    @Test
    fun `test handleCrash calls mark() method when capture_last_run config is enabled`() {
        crash = Crash.ofThrowable(testException, localJsException, "Unity123")
        setupForHandleCrash(false)
        every { ndkService.getUnityCrashId() } returns null

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
