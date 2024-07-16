package io.embrace.android.embracesdk.ndk

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fixtures.testNativeCrashData
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashErrors
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashUnwindError
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.getAttribute
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.payload.NativeCrashDataError
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NativeCrashDataSourceImplTest {
    private val errorSerializerType = Types.newParameterizedType(List::class.java, NativeCrashDataError::class.java)
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var fakeNdkService: FakeNdkService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var configService: FakeConfigService
    private lateinit var serializer: EmbraceSerializer
    private lateinit var logWriter: LogWriter
    private lateinit var logger: EmbLogger
    private lateinit var otelLogger: FakeOpenTelemetryLogger
    private lateinit var sessionIdTracker: SessionIdTracker
    private lateinit var metadataService: FakeMetadataService
    private lateinit var nativeCrashDataSource: NativeCrashDataSourceImpl

    @Before
    fun setUp() {
        sessionProperties = fakeEmbraceSessionProperties()
        fakeNdkService = FakeNdkService()
        preferencesService = FakePreferenceService()
        logger = EmbLoggerImpl()
        sessionIdTracker = FakeSessionIdTracker().apply { setActiveSessionId("currentSessionId", true) }
        metadataService = FakeMetadataService()
        otelLogger = FakeOpenTelemetryLogger()
        logWriter = LogWriterImpl(
            sessionIdTracker = sessionIdTracker,
            metadataService = metadataService,
            logger = otelLogger
        )
        configService = FakeConfigService()
        serializer = EmbraceSerializer()
        nativeCrashDataSource = NativeCrashDataSourceImpl(
            sessionProperties = sessionProperties,
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
        fakeNdkService.setNativeCrashData(testNativeCrashData)
        assertNotNull(nativeCrashDataSource.getAndSendNativeCrash())

        with(otelLogger.builders.single()) {
            assertEquals(1, emitCalled)
            assertTrue(attributes.hasFixedAttribute(EmbType.System.NativeCrash))
            assertNotNull(attributes.getAttribute(LogIncubatingAttributes.LOG_RECORD_UID))
            assertEquals(testNativeCrashData.sessionId, attributes.getAttribute(embSessionId))
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
}
