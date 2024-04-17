package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.opentelemetry.api.logs.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogWriterImplTest {
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var sessionIdTracker: SessionIdTracker
    private lateinit var logWriterImpl: LogWriterImpl
    private lateinit var metadataService: FakeMetadataService

    @Before
    fun setup() {
        sessionIdTracker = FakeSessionIdTracker()
        logger = FakeOpenTelemetryLogger()
        metadataService = FakeMetadataService()
        logWriterImpl = LogWriterImpl(
            logger = logger,
            sessionIdTracker = sessionIdTracker,
            metadataService = metadataService,
        )
    }

    @Test
    fun `check no-op if mapper function required`() {
        val aeiInfo = AppExitInfoData(
            sessionId = "fakeSessionid",
            sessionIdError = null,
            importance = null,
            pss = null,
            reason = null,
            rss = null,
            status = null,
            timestamp = null,
            trace = null,
            description = null,
            traceStatus = null
        )
        logWriterImpl.addLog(aeiInfo)
        assertEquals(0, logger.builders.size)
    }

    @Test
    fun `check expected values added to every OTel log`() {
        sessionIdTracker.setActiveSessionId("session-id", true)
        val logEventData = LogEventData(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    configService = FakeConfigService(),
                    customAttributes = mapOf(PrivateSpan.toEmbraceKeyValuePair())
                )
            ),
            severity = io.embrace.android.embracesdk.Severity.ERROR,
            message = "test"
        )
        logWriterImpl.addLog(logEventData)
        with(logger.builders.single()) {
            assertEquals("test", body)
            assertEquals(Severity.ERROR, severity)
            assertEquals(Severity.ERROR.name, severity.name)
            assertEquals("session-id", attributes[embSessionId.name])
            assertTrue(attributes.hasFixedAttribute(PrivateSpan))
        }
    }
}
