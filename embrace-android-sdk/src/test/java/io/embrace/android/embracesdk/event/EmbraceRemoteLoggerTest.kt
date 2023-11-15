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
import org.junit.Assert
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
        metadataService.setActiveSessionId("session-123")
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
            deliveryService.sendLogs(
                withArg {
                    Assert.assertEquals("Hello world", it.event.name)
                    Assert.assertNotNull(it.event.timestamp)
                    Assert.assertEquals(EmbraceEvent.Type.INFO_LOG, it.event.type)
                    Assert.assertEquals(props, it.event.customPropertiesMap)
                    Assert.assertNotNull(it.event.messageId)
                    Assert.assertNotNull(it.event.eventId)
                    Assert.assertNotNull(it.event.sessionId)
                    it.event.screenshotTaken?.let { Assert.assertFalse(it) }
                    Assert.assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
                }
            )
        }

        verify {
            deliveryService.sendLogs(
                withArg {
                    Assert.assertEquals("Warning world", it.event.name)
                    Assert.assertEquals(EmbraceEvent.Type.WARNING_LOG, it.event.type)
                    Assert.assertNull(it.event.customPropertiesMap)
                    Assert.assertNotNull(it.event.messageId)
                    Assert.assertNotNull(it.event.eventId)
                    Assert.assertNotNull(it.event.sessionId)
                    it.event.screenshotTaken?.let { st -> Assert.assertFalse(st) }
                    Assert.assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
                }
            )
        }

        verify {
            deliveryService.sendLogs(
                withArg {
                    Assert.assertEquals("Hello errors", it.event.name)
                    Assert.assertEquals(EmbraceEvent.Type.ERROR_LOG, it.event.type)
                    Assert.assertNull(it.event.customPropertiesMap)
                    Assert.assertNotNull(it.event.messageId)
                    Assert.assertNotNull(it.event.eventId)
                    Assert.assertNotNull(it.event.sessionId)
                    it.event.screenshotTaken?.let { Assert.assertFalse(it) }
                    Assert.assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
                }
            )
        }

        // verify sent counts
        Assert.assertEquals(1, remoteLogger.getInfoLogsAttemptedToSend())
        Assert.assertEquals(1, remoteLogger.getWarnLogsAttemptedToSend())
        Assert.assertEquals(1, remoteLogger.getErrorLogsAttemptedToSend())
        Assert.assertEquals(0, remoteLogger.getUnhandledExceptionsSent())
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
            deliveryService.sendLogs(
                withArg {
                    Assert.assertEquals("Hello world", it.event.name)
                    Assert.assertEquals(EmbraceEvent.Type.ERROR_LOG, it.event.type)
                    Assert.assertEquals("NullPointerException", it.event.exceptionName)
                    Assert.assertEquals("exception message", it.event.exceptionMessage)
                    Assert.assertNotNull(it.event.messageId)
                    Assert.assertNotNull(it.event.eventId)
                    Assert.assertNotNull(it.event.sessionId)
                    Assert.assertNotNull(it.event.sessionId)
                    Assert.assertNotNull(it.event.sessionId)
                    Assert.assertNotNull(it.event.logExceptionType)
                    Assert.assertEquals(LogExceptionType.NONE.value, it.event.logExceptionType)
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
                    Assert.assertEquals("appId", it.appId)
                    Assert.assertEquals("session-123", it.sessionId)
                    Assert.assertNotNull(it.appInfo)
                    Assert.assertNotNull(it.networkCaptureCall)
                }
            )
        }

        Assert.assertEquals(1, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun `testLogNetwork with no info`() {
        remoteLogger = getRemoteLogger()
        remoteLogger.logNetwork(null)

        verify(exactly = 0) {
            deliveryService.sendNetworkCall(any())
        }

        Assert.assertEquals(0, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun testDefaultMaxMessageLength() {
        remoteLogger = getRemoteLogger()
        remoteLogger.log("Hi".repeat(65), EmbraceEvent.Type.INFO_LOG, null)

        verify {
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Hi".repeat(62) + "H...")
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
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Hi".repeat(23) + "H...")
                }
            )
        }
    }

    @Test
    fun testLogMessageEabled() {
        every { configService.dataCaptureEventBehavior.isLogMessageEnabled("Hello World") } returns false
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)
        remoteLogger.log("Another", EmbraceEvent.Type.INFO_LOG, null)

        verify {
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Another")
                }
            )
        }

        verify(exactly = 0) {
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Hello World")
                }
            )
        }
    }

    @Test
    fun testMessageTypeEnabled() {
        every { configService.dataCaptureEventBehavior.isMessageTypeEnabled(MessageType.LOG) } returns false
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)

        verify(exactly = 0) { deliveryService.sendLogs(any()) }
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        remoteLogger = getRemoteLogger()

        repeat(500) { k ->
            remoteLogger.log("Test info $k", EmbraceEvent.Type.INFO_LOG, null)
            remoteLogger.log("Test warning $k", EmbraceEvent.Type.WARNING_LOG, null)
            remoteLogger.log("Test error $k", EmbraceEvent.Type.ERROR_LOG, null)
        }

        Assert.assertEquals(100, remoteLogger.findInfoLogIds(0L, Long.MAX_VALUE).size)
        Assert.assertEquals(500, remoteLogger.getInfoLogsAttemptedToSend())
        Assert.assertEquals(100, remoteLogger.findWarningLogIds(0L, Long.MAX_VALUE).size)
        Assert.assertEquals(500, remoteLogger.getWarnLogsAttemptedToSend())
        Assert.assertEquals(250, remoteLogger.findErrorLogIds(0L, Long.MAX_VALUE).size)
        Assert.assertEquals(500, remoteLogger.getErrorLogsAttemptedToSend())
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
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Unity".repeat(1000)) // log limit higher on unity
                    Assert.assertTrue(it.stacktraces?.unityStacktrace == "my stacktrace")
                    Assert.assertEquals(LogExceptionType.HANDLED.value, it.event.logExceptionType)
                }
            )
        }

        Assert.assertEquals(0, remoteLogger.getUnhandledExceptionsSent())
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
            deliveryService.sendLogs(
                withArg {
                    Assert.assertTrue(it.event.name == "Unity".repeat(1000)) // log limit higher on unity
                    Assert.assertTrue(it.stacktraces?.unityStacktrace == "my stacktrace")
                    Assert.assertEquals(LogExceptionType.UNHANDLED.value, it.event.logExceptionType)
                }
            )
        }

        Assert.assertEquals(1, remoteLogger.getUnhandledExceptionsSent())
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
        verify(exactly = 1) { deliveryService.sendLogs(capture(action)) }
        val msg = action.captured
        Assert.assertEquals("Dart error name", msg.event.exceptionName)
        Assert.assertEquals("Dart error message", msg.event.exceptionMessage)
        Assert.assertEquals("my stacktrace", msg.stacktraces?.flutterStacktrace)
        Assert.assertEquals("dart context", msg.stacktraces?.context)
        Assert.assertEquals("dart library", msg.stacktraces?.library)
        Assert.assertEquals(1, remoteLogger.getUnhandledExceptionsSent())
    }

    @Test
    fun testIfShouldGateInfoLog() {
        remoteLogger = getRemoteLogger()
        cfg = buildCustomRemoteConfig(
            setOf(),
            null
        )
        Assert.assertTrue(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.INFO_LOG))
        Assert.assertTrue(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.WARNING_LOG))
    }

    @Test
    fun testIfShouldNotGateInfoLog() {
        remoteLogger = getRemoteLogger()
        cfg = buildCustomRemoteConfig(
            setOf(SessionGatingKeys.LOGS_INFO, SessionGatingKeys.LOGS_WARN),
            null
        )
        Assert.assertFalse(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.INFO_LOG))
        Assert.assertFalse(remoteLogger.checkIfShouldGateLog(EmbraceEvent.Type.WARNING_LOG))
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>?) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                true,
                false,
                components,
                fullSessionEvents
            )
        )
}
