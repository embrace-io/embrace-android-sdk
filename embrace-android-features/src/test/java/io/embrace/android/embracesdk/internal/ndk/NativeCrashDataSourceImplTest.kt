package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fixtures.testNativeCrashData
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashErrors
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashUnwindError
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError
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
    private val errorSerializerType = TypeUtils.typedList(NativeCrashDataError::class)
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var fakeNdkService: FakeNdkService
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

    @Before
    fun setUp() {
        sessionPropertiesService = FakeSessionPropertiesService()
        fakeNdkService = FakeNdkService()
        preferencesService = FakePreferenceService()
        logger = EmbLoggerImpl()
        sessionIdTracker = FakeSessionIdTracker().apply { setActiveSession("currentSessionId", true) }
        metadataService = FakeMetadataService()
        processStateService = FakeProcessStateService()
        otelLogger = FakeOpenTelemetryLogger()
        logWriter = LogWriterImpl(
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService,
            logger = otelLogger
        )
        configService = FakeConfigService()
        serializer = EmbraceSerializer()
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            sessionPropertiesService = sessionPropertiesService,
            ndkService = fakeNdkService,
            preferencesService = preferencesService,
            logWriter = logWriter,
            configService = configService,
            serializer = serializer,
            logger = logger
        )
    }

    @Test
    fun `native crash sent when there is one to be found`() {
        fakeNdkService.addNativeCrashData(testNativeCrashData)
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertEquals(testNativeCrashData.timestamp, timestampEpochNanos.nanosToMillis())
            assertEquals(testNativeCrashData.appState, attributes.getAttribute(embState))
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertEquals(testNativeCrashData.sessionId, attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertEquals(testNativeCrashData.crash, attributes.getAttribute(embNativeCrashException))
            assertEquals(
                serializer.toJson(testNativeCrashData.errors, errorSerializerType).toByteArray().toUTF8String(),
                attributes.getAttribute(embNativeCrashErrors)
            )
            assertEquals(
                serializer.toJson(testNativeCrashData.symbols, Map::class.java).toByteArray().toUTF8String(),
                attributes.getAttribute(embNativeCrashSymbols)
            )
            assertEquals(testNativeCrashData.unwindError.toString(), attributes.getAttribute(embNativeCrashUnwindError))
        }
    }

    @Test
    fun `native crash sent without attributes that are null`() {
        fakeNdkService.addNativeCrashData(
            NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "null",
                timestamp = 1700000000000,
                appState = null,
                metadata = null,
                unwindError = null,
                crash = null,
                symbols = null,
                errors = null,
                map = null
            )
        )
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertNull(attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertNull(attributes.getAttribute(embNativeCrashException))
            assertNull(attributes.getAttribute(embNativeCrashErrors))
            assertNull(attributes.getAttribute(embNativeCrashSymbols))
            assertNull(attributes.getAttribute(embNativeCrashUnwindError))
        }
    }

    @Test
    fun `native crash sent without attributes that are blankish`() {
        fakeNdkService.addNativeCrashData(
            NativeCrashData(
                nativeCrashId = "nativeCrashId",
                sessionId = "",
                timestamp = 1700000000000,
                appState = "",
                metadata = null,
                unwindError = null,
                crash = "",
                symbols = emptyMap(),
                errors = emptyList(),
                map = ""
            )
        )
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertNull(attributes.getAttribute(SessionIncubatingAttributes.SESSION_ID))
            assertEquals("1", attributes.getAttribute(embCrashNumber))
            assertNull(attributes.getAttribute(embState))
            assertNull(attributes.getAttribute(embNativeCrashException))
            assertNull(attributes.getAttribute(embNativeCrashErrors))
            assertNull(attributes.getAttribute(embNativeCrashSymbols))
            assertNull(attributes.getAttribute(embNativeCrashUnwindError))
        }
    }
}
