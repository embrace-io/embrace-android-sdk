package io.embrace.android.embracesdk.event

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
        private lateinit var deliveryService: FakeDeliveryService
        private lateinit var userService: UserService
        private lateinit var configService: ConfigService
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
            userService = FakeUserService()
            logcat = InternalEmbraceLogger()
            executor = Executors.newSingleThreadExecutor()
            tick = AtomicLong(1609823408L)
            clock = Clock { tick.incrementAndGet() }
            mockkStatic(Uuid::class)
            every { Uuid.getEmbUuid() } returns "id"
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private lateinit var cfg: RemoteConfig

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )
        deliveryService = FakeDeliveryService()

        cfg = RemoteConfig()

        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                cfg
            },
            logMessageBehavior = fakeLogMessageBehavior {
                cfg.logConfig
            },
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior {
                cfg
            }
        )
        gatingService = EmbraceGatingService(configService)
        metadataService.setActiveSessionId("session-123", true)
        metadataService.setAppForeground()
        metadataService.setAppId("appId")
        sessionProperties = EmbraceSessionProperties(FakePreferenceService(), configService)
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
            FakeNetworkConnectivityService()
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

        val logs = deliveryService.lastSentLogs
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.event.name)
        assertNotNull(first.event.timestamp)
        assertEquals(EmbraceEvent.Type.INFO_LOG, first.event.type)
        assertEquals(props, first.event.customProperties)
        assertNotNull(first.event.messageId)
        assertNotNull(first.event.eventId)
        assertNotNull(first.event.sessionId)
        first.event.screenshotTaken?.let { assertFalse(it) }
        assertEquals(LogExceptionType.NONE.value, first.event.logExceptionType)

        val second = logs[1]
        assertEquals("Warning world", second.event.name)
        assertEquals(EmbraceEvent.Type.WARNING_LOG, second.event.type)
        assertNull(second.event.customProperties)
        assertNotNull(second.event.messageId)
        assertNotNull(second.event.eventId)
        assertNotNull(second.event.sessionId)
        second.event.screenshotTaken?.let { st -> assertFalse(st) }
        assertEquals(LogExceptionType.NONE.value, second.event.logExceptionType)

        val third = logs[2]
        assertEquals("Hello errors", third.event.name)
        assertEquals(EmbraceEvent.Type.ERROR_LOG, third.event.type)
        assertNull(third.event.customProperties)
        assertNotNull(third.event.messageId)
        assertNotNull(third.event.eventId)
        assertNotNull(third.event.sessionId)
        third.event.screenshotTaken?.let { assertFalse(it) }
        assertEquals(LogExceptionType.NONE.value, third.event.logExceptionType)

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

        val message = deliveryService.lastSentLogs.single()
        assertEquals("Hello world", message.event.name)
        assertEquals(EmbraceEvent.Type.ERROR_LOG, message.event.type)
        assertEquals("NullPointerException", message.event.exceptionName)
        assertEquals("exception message", message.event.exceptionMessage)
        assertNotNull(message.event.messageId)
        assertNotNull(message.event.eventId)
        assertNotNull(message.event.sessionId)
        assertNotNull(message.event.sessionId)
        assertNotNull(message.event.sessionId)
        assertNotNull(message.event.logExceptionType)
        assertEquals(LogExceptionType.NONE.value, message.event.logExceptionType)
    }

    @Test
    fun testLogNetwork() {
        val networkCaptureCall = NetworkCapturedCall()

        remoteLogger = getRemoteLogger()
        remoteLogger.logNetwork(networkCaptureCall)

        val message = checkNotNull(deliveryService.lastSentNetworkCall)
        assertEquals("appId", message.appId)
        assertEquals("session-123", message.sessionId)
        assertNotNull(message.appInfo)
        assertNotNull(message.networkCaptureCall)

        assertEquals(1, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun `testLogNetwork with no info`() {
        remoteLogger = getRemoteLogger()
        remoteLogger.logNetwork(null)

        assertNull(deliveryService.lastSentNetworkCall)
        assertEquals(0, remoteLogger.findNetworkLogIds(0, clock.now()).size)
    }

    @Test
    fun testDefaultMaxMessageLength() {
        remoteLogger = getRemoteLogger()
        remoteLogger.log("Hi".repeat(65), EmbraceEvent.Type.INFO_LOG, null)

        val message = deliveryService.lastSentLogs.single()
        assertTrue(message.event.name == "Hi".repeat(62) + "H...")
    }

    @Test
    fun testCustomMaxMessageLength() {
        cfg = cfg.copy(
            logConfig = LogRemoteConfig(
                logMessageMaximumAllowedLength = 50,
                logInfoLimit = 50
            )
        )

        remoteLogger = getRemoteLogger()
        remoteLogger.log("Hi".repeat(50), EmbraceEvent.Type.INFO_LOG, null)

        val message = deliveryService.lastSentLogs.single()
        assertTrue(message.event.name == "Hi".repeat(23) + "H...")
    }

    @Test
    fun testLogMessageEnabled() {
        cfg = cfg.copy(disabledEventAndLogPatterns = setOf("Hello World"))
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)
        remoteLogger.log("Another", EmbraceEvent.Type.INFO_LOG, null)

        deliveryService.lastSentLogs.single().let {
            assertEquals("Another", it.event.name)
        }
    }

    @Test
    fun testMessageTypeEnabled() {
        cfg = cfg.copy(disabledMessageTypes = setOf(MessageType.LOG.name.toLowerCase()))
        remoteLogger = getRemoteLogger()

        remoteLogger.log("Hello World", EmbraceEvent.Type.INFO_LOG, null)
        assertEquals(0, deliveryService.lastSentLogs.size)
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

        val message = deliveryService.lastSentLogs.single()
        assertEquals("Unity".repeat(1000), message.event.name) // log limit higher on unity
        assertEquals("my stacktrace", message.stacktraces?.unityStacktrace)
        assertEquals(LogExceptionType.HANDLED.value, message.event.logExceptionType)

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

        val message = deliveryService.lastSentLogs.single()
        assertTrue(message.event.name == "Unity".repeat(1000)) // log limit higher on unity
        assertTrue(message.stacktraces?.unityStacktrace == "my stacktrace")
        assertEquals(LogExceptionType.UNHANDLED.value, message.event.logExceptionType)

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

        val msg = deliveryService.lastSentLogs.single()
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
