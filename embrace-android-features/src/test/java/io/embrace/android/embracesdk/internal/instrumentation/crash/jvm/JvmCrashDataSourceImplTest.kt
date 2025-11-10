package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCrashFileMarker
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeLogOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(IncubatingApi::class)
internal class JvmCrashDataSourceImplTest {

    private lateinit var crashDataSource: JvmCrashDataSourceImpl
    private lateinit var logOrchestrator: FakeLogOrchestrator
    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var anrService: FakeAnrService
    private lateinit var keyValueStore: FakeKeyValueStore
    private lateinit var crashMarker: FakeCrashFileMarker
    private lateinit var serializer: EmbraceSerializer
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var logger: EmbLogger
    private lateinit var testException: Exception
    private var telemetryModifier: ((TelemetryAttributes) -> SchemaType)? = null

    @Before
    fun setUp() {
        logOrchestrator = FakeLogOrchestrator()
        sessionOrchestrator = FakeSessionOrchestrator()
        sessionPropertiesService = FakeSessionPropertiesService()
        anrService = FakeAnrService()
        keyValueStore = FakeKeyValueStore()
        crashMarker = FakeCrashFileMarker()
        args = FakeInstrumentationArgs(mockk())
        serializer = EmbraceSerializer()
        logger = EmbLoggerImpl()
        testException = RuntimeException("Test exception")
    }

    private fun setupForHandleCrash(crashHandlerEnabled: Boolean = false) {
        args = FakeInstrumentationArgs(
            mockk(),
            configService = FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(
                    uncaughtExceptionHandlerEnabled = crashHandlerEnabled
                )
            )
        )
        crashDataSource = JvmCrashDataSourceImpl(
            sessionPropertiesService,
            keyValueStore,
            args,
            serializer,
            telemetryModifier,
        ).apply {
            addCrashTeardownHandler(logOrchestrator)
            addCrashTeardownHandler(sessionOrchestrator)
            addCrashTeardownHandler(crashMarker)
            addCrashTeardownHandler(anrService)
        }
    }

    @Test
    fun `test crash handler order`() {
        setupForHandleCrash()
        val observedOrder = mutableListOf<Int>()
        crashDataSource.addCrashTeardownHandler { observedOrder.add(1) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(2) }
        crashDataSource.addCrashTeardownHandler { observedOrder.add(3) }
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(listOf(1, 2, 3), observedOrder)
    }

    @Test
    fun `test SessionOrchestrator and LogOrchestrator are called when handleCrash is called`() {
        setupForHandleCrash()

        crashDataSource.logUnhandledJvmThrowable(testException)

        assertEquals(1, anrService.crashCount)
        assertEquals(1, args.destination.logEvents.size)
        assertTrue(logOrchestrator.flushCalled)
        assertNotNull(sessionOrchestrator.crashId)
    }

    @Test
    fun `test LogWriter and SessionOrchestrator are called when handleCrash is called with JSException`() {
        setupForHandleCrash()
        crashDataSource.logUnhandledJvmThrowable(testException)

        assertEquals(1, anrService.crashCount)
        val destination = args.destination
        assertEquals(1, destination.logEvents.size)
        val lastSentCrash = destination.logEvents.single()
        assertEquals(
            destination.logEvents.single().schemaType.attributes()[LogAttributes.LOG_RECORD_UID],
            sessionOrchestrator.crashId
        )

        /*
         * Verify mainCrashHandled is true after the first execution
         * by testing that a second execution of handleCrash wont run anything
         */
        crashDataSource.logUnhandledJvmThrowable(testException)
        assertEquals(1, anrService.crashCount)
        assertEquals(1, destination.logEvents.size)
        assertSame(lastSentCrash, destination.logEvents.single())
    }

    @Test
    fun `test handleCrash calls mark() method when capture_last_run config is enabled`() {
        setupForHandleCrash()

        crashDataSource.logUnhandledJvmThrowable(testException)

        assertTrue(crashMarker.isMarked())
    }

    @Test
    fun `test RN crash by calling logUnhandledJsException() before handleCrash()`() {
        val jsCrashService = JsCrashServiceImpl(serializer)
        jsCrashService.logUnhandledJsException(
            "NullPointerException",
            "Null pointer exception occurred",
            "RuntimeException",
            "at com.example.MyClass.method(MyClass.kt:10)"
        )
        telemetryModifier = jsCrashService::appendCrashTelemetryAttributes
        setupForHandleCrash()

        crashDataSource.logUnhandledJvmThrowable(testException)

        val destination = args.destination
        val logEvent = destination.logEvents.single()
        assertEquals(EmbType.System.ReactNativeCrash, logEvent.schemaType.telemetryType)
        val lastSentCrashAttributes = logEvent.schemaType.attributes()
        assertEquals(1, anrService.crashCount)
        assertEquals(1, destination.logEvents.size)
        assertEquals(
            lastSentCrashAttributes[LogAttributes.LOG_RECORD_UID],
            sessionOrchestrator.crashId
        )
        assertEquals(
            "{\"n\":\"NullPointerException\",\"" +
                "m\":\"Null pointer exception occurred\",\"" +
                "t\":\"RuntimeException\",\"" +
                "st\":\"" +
                "at com.example.MyClass.method(MyClass.kt:10)\"}",
            lastSentCrashAttributes["emb.android.react_native_crash.js_exception"]
        )
    }

    @Test
    fun `test exception handler is registered with config option enabled`() {
        setupForHandleCrash(true)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
    }

    @Test
    fun `test exception handler is not registered with config option disabled`() {
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        setupForHandleCrash(false)
        assert(Thread.getDefaultUncaughtExceptionHandler() !is EmbraceUncaughtExceptionHandler)
    }
}
