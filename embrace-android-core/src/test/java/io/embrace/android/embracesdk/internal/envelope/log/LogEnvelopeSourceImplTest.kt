package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fakes.fakeEnvelopeMetadata
import io.embrace.android.embracesdk.fakes.fakeEnvelopeResource
import io.embrace.android.embracesdk.fixtures.nativeCrashLog
import io.embrace.android.embracesdk.fixtures.nativeCrashWithoutSessionLog
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogEnvelopeSourceImplTest {

    private val fakeBatchedPayload = LogPayload(logs = listOf(testLog))
    private lateinit var metadataSource: FakeEnvelopeMetadataSource
    private lateinit var resourceSource: FakeEnvelopeResourceSource
    private lateinit var logSource: FakeLogPayloadSource
    private lateinit var cachedLogEnvelopeStore: FakeCachedLogEnvelopeStore
    private lateinit var logEnvelopeSource: LogEnvelopeSourceImpl

    @Before
    fun setup() {
        metadataSource = FakeEnvelopeMetadataSource()
        resourceSource = FakeEnvelopeResourceSource()
        logSource = FakeLogPayloadSource().apply {
            batchedLogPayloadSource = fakeBatchedPayload
        }
        cachedLogEnvelopeStore = FakeCachedLogEnvelopeStore()
        logEnvelopeSource = LogEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            logSource,
            cachedLogEnvelopeStore,
        )
    }

    @Test
    fun getBatchedLogEnvelope() {
        with(logEnvelopeSource.getBatchedLogEnvelope()) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(fakeBatchedPayload, data)
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun getSingleLogEnvelopes() {
        logSource.singleLogPayloadsSource = mutableListOf<LogRequest<LogPayload>>().apply {
            repeat(5) {
                add(LogRequest(LogPayload(listOf(sendImmediatelyLog.copy(body = "$it")))))
            }
        }
        with(logEnvelopeSource.getSingleLogEnvelopes().first().payload) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(sendImmediatelyLog.copy(body = "0"), data.logs?.single())
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
        with(logEnvelopeSource.getSingleLogEnvelopes().last().payload) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(sendImmediatelyLog.copy(body = "4"), data.logs?.single())
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun `check empty log envelope`() {
        with(logEnvelopeSource.getEmptySingleLogEnvelope()) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertTrue(checkNotNull(data.logs).isEmpty())
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun `check native crash envelope`() {
        val crashPayload = LogPayload(logs = listOf(nativeCrashLog))
        val crashLogAttributes = checkNotNull(nativeCrashLog.attributes)
        val expectedSessionId = crashLogAttributes.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
        val expectedProcessIdentifier = crashLogAttributes.findAttributeValue(embProcessIdentifier.name)
        val cachedCrashEnvelopeMetadata = createNativeCrashEnvelopeMetadata(
            sessionId = expectedSessionId,
            processIdentifier = expectedProcessIdentifier,
        )
        logSource.singleLogPayloadsSource = listOf(
            LogRequest(crashPayload)
        )
        cachedLogEnvelopeStore.create(
            storedTelemetryMetadata = cachedCrashEnvelopeMetadata,
            resource = fakeEnvelopeResource,
            metadata = fakeEnvelopeMetadata
        )
        with(logEnvelopeSource.getSingleLogEnvelopes().first().payload) {
            assertEquals(fakeEnvelopeResource, resource)
            assertEquals(fakeEnvelopeMetadata, metadata)
            assertEquals(nativeCrashLog, data.logs?.single())
        }

        with(cachedLogEnvelopeStore.envelopeGetRequest.single()) {
            assertEquals(0L, timestamp)
            assertEquals(expectedSessionId, uuid)
            assertEquals(expectedProcessIdentifier, processId)
            assertEquals(SupportedEnvelopeType.CRASH, envelopeType)
            assertEquals(PayloadType.NATIVE_CRASH, payloadType)
        }
    }

    @Test
    fun `check native crash envelope when no matching session found`() {
        logSource.singleLogPayloadsSource = listOf(
            LogRequest(LogPayload(logs = listOf(nativeCrashWithoutSessionLog)))
        )
        cachedLogEnvelopeStore.create(
            storedTelemetryMetadata = createNativeCrashEnvelopeMetadata(),
            resource = fakeEnvelopeResource,
            metadata = fakeEnvelopeMetadata
        )
        with(logEnvelopeSource.getSingleLogEnvelopes().first().payload) {
            assertEquals(fakeEnvelopeResource, resource)
            assertEquals(fakeEnvelopeMetadata, metadata)
            assertEquals(nativeCrashWithoutSessionLog, data.logs?.single())
        }

        with(cachedLogEnvelopeStore.envelopeGetRequest.single()) {
            assertEquals(0L, timestamp)
            assertEquals("none", uuid)
            assertEquals("none", processId)
            assertEquals(SupportedEnvelopeType.CRASH, envelopeType)
            assertEquals(PayloadType.NATIVE_CRASH, payloadType)
        }
    }
}
