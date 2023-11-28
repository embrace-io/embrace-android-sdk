package io.embrace.android.embracesdk.event

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class EmbraceRemoteLoggerTest {

    companion object {
        private lateinit var remoteLogger: EmbraceRemoteLogger
        private lateinit var metadataService: FakeAndroidMetadataService
        private lateinit var deliveryService: EmbraceDeliveryService
        private lateinit var userService: UserService
        private lateinit var configService: ConfigService
        private lateinit var memoryCleanerService: MemoryCleanerService
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var gatingService: EmbraceGatingService
        private lateinit var logcat: InternalEmbraceLogger
        private lateinit var executor: ExecutorService
        private lateinit var tick: AtomicLong
        private lateinit var clock: Clock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            metadataService = FakeAndroidMetadataService()
            deliveryService = mockk(relaxed = true)
            userService = mockk(relaxed = true)
            memoryCleanerService = mockk(relaxed = true)
            sessionProperties = mockk(relaxed = true)
            logcat = mockk(relaxed = true)
            configService = mockk(relaxed = true) {
                every { sessionBehavior } returns fakeSessionBehavior()
            }
            executor = Executors.newSingleThreadExecutor()
            tick = AtomicLong(1609823408L)
            clock = Clock { tick.incrementAndGet() }
            mockkStatic(Uuid::class)
            every { Uuid.getEmbUuid() } returns "id"

            gatingService = EmbraceGatingService(
                configService
            )
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        every { configService.sessionBehavior } returns fakeSessionBehavior {
            cfg
        }
        every { configService.logMessageBehavior } returns fakeLogMessageBehavior {
            LogRemoteConfig()
        }
        every { configService.dataCaptureEventBehavior.isLogMessageEnabled(any()) } returns true
        every { configService.dataCaptureEventBehavior.isMessageTypeEnabled(any()) } returns true
        metadataService.setActiveSessionId("session-123", true)
        metadataService.setAppForeground()
        metadataService.setAppId("appId")
    }

    private fun getRemoteLogger(): EmbraceRemoteLogger {
        return EmbraceRemoteLogger(
            metadataService,
            deliveryService,
            userService,
            configService,
            sessionProperties,
            logcat,
            clock,
            MoreExecutors.newDirectExecutorService(),
            gatingService,
            mockk(relaxed = true)
        )
    }

    @Test
    fun testLogSimple() {
        every { Uuid.getEmbUuid() } returns "id"
        remoteLogger = getRemoteLogger()

        val props = mapOf("foo" to "bar")
        remoteLogger.log("Hello world", EmbraceEvent.Type.INFO_LOG, props)
        remoteLogger.log("Warning world", EmbraceEvent.Type.WARNING_LOG, null)
        remoteLogger.log("Hello errors", EmbraceEvent.Type.ERROR_LOG, null)

        verify {
            deliveryService.sendLog(
                withArg { eventMessage ->
                    assertEquals("Hello world", eventMessage.event.name)
                    assertNotNull(eventMessage.event.timestamp)
                    assertEquals(EmbraceEvent.Type.INFO_LOG, eventMessage.event.type)
                    assertEquals(props, eventMessage.event.customPropertiesMap)
                    assertNotNull(eventMessage.event.messageId)
                    assertNotNull(eventMessage.event.eventId)
                    assertNotNull(eventMessage.event.sessionId)
                    eventMessage.event.screenshotTaken?.let { assertFalse(it) }
                    assertEquals(LogExceptionType.NONE.value, eventMessage.event.logExceptionType)
                }
            )
        }

        verify {
            deliveryService.sendLog(
                withArg {
                    assertEquals("Warning world", it.event.name)
                    assertEquals(EmbraceEvent.Type.WARNING_LOG, it.event.type)
                    assertNull(it.event.customPropertiesMap)
                    assertNotNull(it.event.messageId)
                    assertNotNull(it.event.eventId)
                    assertNotNull(it.event.sessionId)
                    it.event.screenshotTaken?.let { st -> assertFalse(st) }
                    assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
                }
            )
        }

        verify {
            deliveryService.sendLog(
                withArg { eventMessage ->
                    assertEquals("Hello errors", eventMessage.event.name)
                    assertEquals(EmbraceEvent.Type.ERROR_LOG, eventMessage.event.type)
                    assertNull(eventMessage.event.customPropertiesMap)
                    assertNotNull(eventMessage.event.messageId)
                    assertNotNull(eventMessage.event.eventId)
                    assertNotNull(eventMessage.event.sessionId)
                    eventMessage.event.screenshotTaken?.let { assertFalse(it) }
                    assertEquals(LogExceptionType.NONE.value, eventMessage.event.logExceptionType)
                }
            )
        }

        // verify sent counts
        assertEquals(1, remoteLogger.getInfoLogsAttemptedToSend())
        assertEquals(1, remoteLogger.getWarnLogsAttemptedToSend())
        assertEquals(1, remoteLogger.getErrorLogsAttemptedToSend())
        assertEquals(0, remoteLogger.getUnhandledExceptionsSent())
    }

    @Test
    fun testExceptionLog() {
        remoteLogger = getRemoteLogger()
        val exception = NullPointerException("exception message")

        remoteLogger.log(
            "Hello world",
            EmbraceEvent.Type.ERROR_LOG,
            LogExceptionType.NONE,
            null,
            exception.stackTrace,
            null,
            Embrace.AppFramework.NATIVE,
            null,
            null,
            exception.javaClass.simpleName,
            exception.message,
        )

        verify {
            deliveryService.sendLog(
                withArg {
                    assertEquals("Hello world", it.event.name)
                    assertEquals(EmbraceEvent.Type.ERROR_LOG, it.event.type)
                    assertEquals("NullPointerException", it.event.exceptionName)
                    assertEquals("exception message", it.event.exceptionMessage)
                    assertNotNull(it.event.messageId)
                    assertNotNull(it.event.eventId)
                    assertNotNull(it.event.sessionId)
                    assertNotNull(it.event.sessionId)
                    assertNotNull(it.event.sessionId)
                    assertNotNull(it.event.logExceptionType)
                    assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
                }
            )
        }
    }

    @Test
    fun testLogNetwork() {
        val networkCaptureCall: NetworkCapturedCall = mockk(relaxed = true)

        remoteLogger = getRemoteLogger()
        remoteLogger.logNetwork(networkCaptureCall)

        verify {
            deliveryService.sendNetworkCall(
                withArg {
                    assertEquals("appId", it.appId)
                    assertEquals("session-123", it.sessionId)
                    assertNotNull(it.appInfo)
                    assertNotNull(it.networkCaptureCall)
                }
            )
        }

        assertEquals(1, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun `testLogNetwork with no info`() {
        remoteLogger = getRemoteLogger()
        remoteLogger.logNetwork(null)

        verify(exactly = 0) {
            deliveryService.sendNetworkCall(any())
        }

        assertEquals(0, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun testDefaultMaxMessageLength() {
        remoteLogger = getRemoteLogger()
        remoteLogger.log("Hi".repeat(65), EmbraceEvent.Type.INFO_LOG, null)

        verify {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Hi".repeat(62) + "H...")
                }
            )
        }
    }

    @Test
    fun testCustomMaxMessageLength() {
        every { configService.logMessageBehavior.getInfoLogLimit() } returns 50
        every { configService.logMessageBehavior.getLogMessageMaximumAllowedLength() } returns 50

        remoteLogger = getRemoteLogger()
        remoteLogger.log("Hi".repeat(50), EmbraceEvent.Type.INFO_LOG, null)

        verify {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Hi".repeat(23) + "H...")
                }
            )
        }
    }

    @Test
    fun testLogMessageEnabled() {
        every { configService.dataCaptureEventBehavior.isLogMessageEnabled("Hello World") } returns false
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)
        remoteLogger.log("Another", EmbraceEvent.Type.INFO_LOG, null)

        verify {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Another")
                }
            )
        }

        verify(exactly = 0) {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Hello World")
                }
            )
        }
    }

    @Test
    fun testMessageTypeEnabled() {
        every { configService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.LOG) } returns false
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)

        verify(exactly = 0) { deliveryService.sendLog(any()) }
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        remoteLogger = getRemoteLogger()

        repeat(500) { k ->
            remoteLogger.log("Test info $k", EmbraceEvent.Type.INFO_LOG, null)
            remoteLogger.log("Test warning $k", EmbraceEvent.Type.WARNING_LOG, null)
            remoteLogger.log("Test error $k", EmbraceEvent.Type.ERROR_LOG, null)
        }

        assertEquals(100, remoteLogger.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, remoteLogger.getInfoLogsAttemptedToSend())
        assertEquals(100, remoteLogger.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, remoteLogger.getWarnLogsAttemptedToSend())
        assertEquals(250, remoteLogger.findErrorLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, remoteLogger.getErrorLogsAttemptedToSend())
    }

    @Test
    fun testLoggingUnityMessage() {
        remoteLogger = getRemoteLogger()

        remoteLogger.log(
            "Unity".repeat(1000),
            EmbraceEvent.Type.INFO_LOG,
            LogExceptionType.HANDLED,
            null,
            null,
            "my stacktrace",
            Embrace.AppFramework.UNITY,
            null,
            null,
            null,
            null
        )

        verify {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Unity".repeat(1000)) // log limit higher on unity
                    assertTrue(it.stacktraces?.unityStacktrace == "my stacktrace")
                    assertEquals(LogExceptionType.HANDLED.value, it.event.logExceptionType)
                }
            )
        }

        assertEquals(0, remoteLogger.getUnhandledExceptionsSent())
    }

    @Test
    fun testLoggingUnityUnhandledException() {
        remoteLogger = getRemoteLogger()

        remoteLogger.log(
            "Unity".repeat(1000),
            EmbraceEvent.Type.INFO_LOG,
            LogExceptionType.UNHANDLED,
            null,
            null,
            "my stacktrace",
            Embrace.AppFramework.UNITY,
            null,
            null,
            null,
            null
        )

        verify {
            deliveryService.sendLog(
                withArg {
                    assertTrue(it.event.name == "Unity".repeat(1000)) // log limit higher on unity
                    assertTrue(it.stacktraces?.unityStacktrace == "my stacktrace")
                    assertEquals(LogExceptionType.UNHANDLED.value, it.event.logExceptionType)
                }
            )
        }

        assertEquals(1, remoteLogger.getUnhandledExceptionsSent())
    }

    @Test
    fun testLoggingFlutterMessage() {
        remoteLogger = getRemoteLogger()
        remoteLogger.log(
            "Dart error",
            EmbraceEvent.Type.ERROR_LOG,
            LogExceptionType.UNHANDLED,
            null,
            null,
            "my stacktrace",
            Embrace.AppFramework.FLUTTER,
            "dart context",
            "dart library",
            "Dart error name",
            "Dart error message"
        )
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        val action = slot<EventMessage>()
        verify(exactly = 1) { deliveryService.sendLog(capture(action)) }
        val msg = action.captured
        assertEquals("Dart error name", msg.event.exceptionName)
        assertEquals("Dart error message", msg.event.exceptionMessage)
        assertEquals("my stacktrace", msg.stacktraces?.flutterStacktrace)
        assertEquals("dart context", msg.stacktraces?.context)
        assertEquals("dart library", msg.stacktraces?.library)
        assertEquals(1, remoteLogger.getUnhandledExceptionsSent())
    }

    @Test
    fun testIfShouldGateInfoLog() {
        remoteLogger = getRemoteLogger()
        cfg = buildCustomRemoteConfig(setOf())
        assertTrue(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.INFO_LOG))
        assertTrue(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.WARNING_LOG))
    }

    @Test
    fun testIfShouldNotGateInfoLog() {
        remoteLogger = getRemoteLogger()
        cfg = buildCustomRemoteConfig(
            setOf(SessionGatingKeys.LOGS_INFO, SessionGatingKeys.LOGS_WARN)
        )
        assertFalse(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.INFO_LOG))
        assertFalse(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.WARNING_LOG))
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>? = null) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true,
                endAsync = false,
                sessionComponents = components,
                fullSessionEvents = fullSessionEvents
            )
        )
}
