package io.embrace.android.embracesdk.event

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
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

internal class EmbraceLogMessageServiceTest {

    companion object {
        private lateinit var logMessageService: EmbraceLogMessageService
        private lateinit var metadataService: FakeMetadataService
        private lateinit var sessionIdTracker: FakeSessionIdTracker
        private lateinit var deliveryService: FakeDeliveryService
        private lateinit var userService: UserService
        private lateinit var configService: ConfigService
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var gatingService: EmbraceGatingService
        private lateinit var logcat: EmbLogger
        private lateinit var executor: ExecutorService
        private lateinit var tick: AtomicLong
        private lateinit var clock: Clock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            metadataService = FakeMetadataService()
            sessionIdTracker = FakeSessionIdTracker()
            userService = FakeUserService()
            logcat = EmbLoggerImpl()
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
        gatingService = EmbraceGatingService(configService, FakeLogMessageService(), logcat)
        sessionIdTracker.setActiveSessionId("session-123", true)
        metadataService.setAppForeground()
        metadataService.setAppId("appId")
        sessionProperties = EmbraceSessionProperties(FakePreferenceService(), configService, logcat)
    }

    private fun getLogMessageService(): EmbraceLogMessageService {
        return EmbraceLogMessageService(
            metadataService,
            sessionIdTracker,
            deliveryService,
            userService,
            configService,
            sessionProperties,
            logcat,
            clock,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            gatingService,
            FakeNetworkConnectivityService()
        )
    }

    @Test
    fun testLogSimple() {
        every { Uuid.getEmbUuid() } returns "id"
        logMessageService = getLogMessageService()

        val props = mapOf("foo" to "bar")
        simpleLog("Hello world", EventType.INFO_LOG, props)
        simpleLog("Warning world", EventType.WARNING_LOG, null)
        simpleLog("Hello errors", EventType.ERROR_LOG, null)

        val logs = deliveryService.lastSentLogs
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.event.name)
        assertNotNull(first.event.timestamp)
        assertEquals(EventType.INFO_LOG, first.event.type)
        assertEquals(props, first.event.customProperties)
        assertNotNull(first.event.messageId)
        assertNotNull(first.event.eventId)
        assertNotNull(first.event.sessionId)
        first.event.screenshotTaken?.let { assertFalse(it) }
        assertEquals(LogExceptionType.NONE.value, first.event.logExceptionType)

        val second = logs[1]
        assertEquals("Warning world", second.event.name)
        assertEquals(EventType.WARNING_LOG, second.event.type)
        assertNull(second.event.customProperties)
        assertNotNull(second.event.messageId)
        assertNotNull(second.event.eventId)
        assertNotNull(second.event.sessionId)
        second.event.screenshotTaken?.let { st -> assertFalse(st) }
        assertEquals(LogExceptionType.NONE.value, second.event.logExceptionType)

        val third = logs[2]
        assertEquals("Hello errors", third.event.name)
        assertEquals(EventType.ERROR_LOG, third.event.type)
        assertNull(third.event.customProperties)
        assertNotNull(third.event.messageId)
        assertNotNull(third.event.eventId)
        assertNotNull(third.event.sessionId)
        third.event.screenshotTaken?.let { assertFalse(it) }
        assertEquals(LogExceptionType.NONE.value, third.event.logExceptionType)
    }

    @Test
    fun testExceptionLog() {
        logMessageService = getLogMessageService()
        val exception = NullPointerException("exception message")

        logMessageService.log(
            "Hello world",
            EventType.ERROR_LOG,
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
        assertEquals(EventType.ERROR_LOG, message.event.type)
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

        logMessageService = getLogMessageService()
        logMessageService.logNetwork(networkCaptureCall)

        val message = checkNotNull(deliveryService.lastSentNetworkCall)
        assertEquals("appId", message.appId)
        assertEquals("session-123", message.sessionId)
        assertNotNull(message.appInfo)
        assertNotNull(message.networkCaptureCall)
    }

    @Test
    fun `testLogNetwork with no info`() {
        logMessageService = getLogMessageService()
        logMessageService.logNetwork(null)

        assertNull(deliveryService.lastSentNetworkCall)
    }

    @Test
    fun testDefaultMaxMessageLength() {
        logMessageService = getLogMessageService()
        simpleLog("Hi".repeat(65), EventType.INFO_LOG, null)

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

        logMessageService = getLogMessageService()
        simpleLog("Hi".repeat(50), EventType.INFO_LOG, null)

        val message = deliveryService.lastSentLogs.single()
        assertTrue(message.event.name == "Hi".repeat(23) + "H...")
    }

    @Test
    fun testLogMessageEnabled() {
        cfg = cfg.copy(disabledEventAndLogPatterns = setOf("Hello World"))
        logMessageService = getLogMessageService()

        simpleLog("Hello World", EventType.INFO_LOG, null)
        simpleLog("Another", EventType.INFO_LOG, null)

        deliveryService.lastSentLogs.single().let {
            assertEquals("Another", it.event.name)
        }
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        logMessageService = getLogMessageService()

        repeat(500) { k ->
            simpleLog("Test info $k", EventType.INFO_LOG, null)
            simpleLog("Test warning $k", EventType.WARNING_LOG, null)
            simpleLog("Test error $k", EventType.ERROR_LOG, null)
        }
        assertEquals(250, logMessageService.findErrorLogIds(0L, Long.MAX_VALUE).size)
    }

    @Test
    fun testLoggingUnityMessage() {
        logMessageService = getLogMessageService()

        logMessageService.log(
            "Unity".repeat(1000),
            EventType.INFO_LOG,
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
    }

    @Test
    fun testLoggingUnityUnhandledException() {
        logMessageService = getLogMessageService()

        logMessageService.log(
            "Unity".repeat(1000),
            EventType.INFO_LOG,
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
    }

    @Test
    fun testLoggingFlutterMessage() {
        logMessageService = getLogMessageService()
        logMessageService.log(
            "Dart error",
            EventType.ERROR_LOG,
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
    }

    @Test
    fun testIfShouldGateInfoLog() {
        logMessageService = getLogMessageService()
        cfg = buildCustomRemoteConfig(setOf())
        assertTrue(logMessageService.checkIfShouldGateLog(EventType.INFO_LOG))
        assertTrue(logMessageService.checkIfShouldGateLog(EventType.WARNING_LOG))
    }

    @Test
    fun testIfShouldNotGateInfoLog() {
        logMessageService = getLogMessageService()
        cfg = buildCustomRemoteConfig(
            setOf(SessionGatingKeys.LOGS_INFO, SessionGatingKeys.LOGS_WARN)
        )
        assertFalse(logMessageService.checkIfShouldGateLog(EventType.INFO_LOG))
        assertFalse(logMessageService.checkIfShouldGateLog(EventType.WARNING_LOG))
    }

    private fun simpleLog(message: String, severity: EventType, properties: Map<String, Any>?) {
        logMessageService.log(
            message,
            severity,
            LogExceptionType.NONE,
            properties,
            null,
            null,
            Embrace.AppFramework.NATIVE,
            null,
            null,
            null,
            null
        )
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>? = null) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true,
                sessionComponents = components,
                fullSessionEvents = fullSessionEvents
            )
        )
}
