package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class EmbraceLogRecordProcessorTest {

    private val otel = createOpenTelemetry()
    private val skipMetadataKey: ContextKey<Boolean> = otel.context.createKey("emb-skip-log-metadata")
    private val sessionAttributeName = "session-attr"

    private lateinit var processor: EmbraceLogRecordProcessor

    @Before
    fun setup() {
        processor = EmbraceLogRecordProcessor(TestUuidSource()) { skipMetadataKey }
        processor.setMetadataProvider { mapOf(sessionAttributeName to "foo") }
    }

    @Test
    fun `log record id added when absent`() {
        val log = FakeReadWriteLogRecord()
        processor.onEmit(log, otel.context.root())
        assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
    }

    @Test
    fun `existing log record id not overridden`() {
        val log = FakeReadWriteLogRecord().apply {
            setStringAttribute(LogAttributes.LOG_RECORD_UID, "existing")
        }
        processor.onEmit(log, otel.context.root())
        assertEquals("existing", log.attributes[LogAttributes.LOG_RECORD_UID])
    }

    @Test
    fun `current metadata added by default`() {
        val log = FakeReadWriteLogRecord()
        processor.onEmit(log, otel.context.root())
        assertEquals("foo", log.attributes[sessionAttributeName])
    }

    @Test
    fun `current metadata skipped when context opts out`() {
        val log = FakeReadWriteLogRecord()
        val skipContext = otel.context.root().set(skipMetadataKey, true)
        processor.onEmit(log, skipContext)

        assertFalse(log.attributes.containsKey(sessionAttributeName))
        // the UID is still added regardless of the metadata opt-out
        assertNotNull(log.attributes[LogAttributes.LOG_RECORD_UID])
    }
}
