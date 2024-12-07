package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.assertions.findAttributeValue
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNativeCrashProcessor
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fixtures.testNativeCrashData
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.spans.getAttribute
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NativeCrashDataSourceImplTest {

    private lateinit var crashProcessor: FakeNativeCrashProcessor
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var configService: FakeConfigService
    private lateinit var serializer: EmbraceSerializer
    private lateinit var logWriter: LogWriter
    private lateinit var logger: EmbLogger
    private lateinit var otelLogger: FakeOpenTelemetryLogger
    private lateinit var sessionIdTracker: SessionIdTracker
    private lateinit var metadataService: FakeMetadataService
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var nativeCrashDataSource: NativeCrashDataSourceImpl
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        crashProcessor = FakeNativeCrashProcessor()
        preferencesService = FakePreferenceService()
        logger = EmbLoggerImpl()
        sessionIdTracker = FakeSessionIdTracker().apply { setActiveSession("currentSessionId", true) }
        metadataService = FakeMetadataService()
        processStateService = FakeProcessStateService()
        clock = FakeClock()
        otelLogger = FakeOpenTelemetryLogger()
        logWriter = LogWriterImpl(
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService,
            logger = otelLogger,
            clock = clock
        )
        configService = FakeConfigService()
        serializer = EmbraceSerializer()
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            nativeCrashProcessor = crashProcessor,
            preferencesService = preferencesService,
            logWriter = logWriter,
            configService = configService,
            serializer = serializer,
            logger = logger
        )
    }

    @Test
    fun `native crash sent when there is one to be found`() {
        crashProcessor.addNativeCrashData(testNativeCrashData)
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())
        assertEquals(1, otelLogger.builders.single().emitCalled)
    }

    @Test
    fun `native crash sent with session properties`() {
        nativeCrashDataSource.sendNativeCrash(testNativeCrashData, mapOf("prop" to "value"))

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertEquals(testNativeCrashData.timestamp, timestampEpochNanos.nanosToMillis())
            assertEquals(0, observedTimestampEpochNanos.nanosToMillis())
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertEquals("value", attributes.findAttributeValue("prop".toSessionPropertyAttributeName()))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertEquals(testNativeCrashData.sessionId, attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertEquals(testNativeCrashData.crash, attributes.getAttribute(embNativeCrashException))
            assertEquals(
                serializer.toJson(testNativeCrashData.symbols, Map::class.java).toByteArray().toUTF8String(),
                attributes.getAttribute(embNativeCrashSymbols)
            )
        }
    }

    @Test
    fun `native crash sent without attributes that are null`() {
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "null",
                timestamp = 1700000000000,
                crash = null,
                symbols = null,
            ),
            sessionProperties = emptyMap()
        )

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertNull(attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertNull(attributes.getAttribute(embNativeCrashException))
            assertNull(attributes.getAttribute(embNativeCrashSymbols))
        }
    }

    @Test
    fun `native crash sent without attributes that are blankish`() {
        nativeCrashDataSource.sendNativeCrash(
            nativeCrash = NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "",
                timestamp = 1700000000000,
                crash = "",
                symbols = emptyMap(),
            ),
            sessionProperties = emptyMap()
        )

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertNull(attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertNull(attributes.getAttribute(embState))
            assertNull(attributes.getAttribute(embNativeCrashException))
            assertNull(attributes.getAttribute(embNativeCrashSymbols))
        }
    }
}
