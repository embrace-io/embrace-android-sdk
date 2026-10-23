package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ApplicationExitInfo
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.resolved.AeiConfig
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

private const val TIMESTAMP = 15000000000L
private const val PID = 6952
private const val IMPORTANCE = 125
private const val PSS = 1509123409L
private const val RSS = 1123409L
private const val REASON = 4
private const val STATUS = 1
private const val DESCRIPTION = "testDescription"
private const val TRACE = "testInputStream"
private const val SESSION_ID = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
private const val USER_SESSION_ID = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"
private const val PROCESS_SUMMARY_STATE = "${SESSION_ID}_$USER_SESSION_ID"

internal class AeiDataSourceImplTest {

    private lateinit var applicationExitInfoService: AeiDataSourceImpl
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var configService: FakeConfigService

    private val worker = fakeBackgroundWorker()

    private val store = FakeKeyValueStore()

    private var records: () -> List<AeiRecord> = { emptyList() }
    private val recordSource = AeiRecordSource { records() }

    private val aeiRecord = FakeAeiRecord(
        timestamp = TIMESTAMP,
        pid = PID,
        processStateSummary = PROCESS_SUMMARY_STATE.toByteArray(),
        importance = IMPORTANCE,
        pss = PSS,
        reason = REASON,
        rss = RSS,
        status = STATUS,
        description = DESCRIPTION,
        traceProvider = { TRACE.byteInputStream() },
    )

    private fun startApplicationExitInfoService(
        versionChecker: VersionChecker = BuildVersionChecker,
        recordSourceProvider: Provider<AeiRecordSource?> = { recordSource },
        errorHandler: FakeErrorHandler = FakeErrorHandler(),
    ) {
        args = FakeInstrumentationArgs(configService = configService, errorHandler = errorHandler)
        applicationExitInfoService = AeiDataSourceImpl(
            args,
            worker,
            recordSourceProvider,
            store,
            FakeOrdinalStore(),
            versionChecker,
        ).apply(AeiDataSourceImpl::onDataCaptureEnabled)
    }

    @Before
    fun setUp() {
        configService = FakeConfigService()
    }

    @Test
    fun `AEI data capture happy path`() {
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService()

        // when getCapturedData is called
        val attrs = getAeiLogAttrs()
        assertEquals(TIMESTAMP.toString(), attrs[EmbAeiAttributes.TIMESTAMP])
        assertEquals(SESSION_ID, attrs[EmbAeiAttributes.AEI_SESSION_PART_ID])
        assertEquals(USER_SESSION_ID, attrs[EmbAeiAttributes.AEI_USER_SESSION_ID])
        assertEquals("", attrs[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("", attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals(IMPORTANCE.toString(), attrs[EmbAeiAttributes.PROCESS_IMPORTANCE])
        assertEquals(PSS.toString(), attrs[EmbAeiAttributes.PSS])
        assertEquals(RSS.toString(), attrs[EmbAeiAttributes.RSS])
        assertEquals(STATUS.toString(), attrs[EmbAeiAttributes.EXIT_STATUS])
        assertEquals(DESCRIPTION, attrs[EmbAeiAttributes.DESCRIPTION])
        assertEquals("", attrs[EmbAeiAttributes.SESSION_ID_ERROR])
        assertNull(attrs[EmbAeiAttributes.TRACE_STATUS])

        val logEventData = args.destination.logEvents.single()
        assertEquals(TRACE, logEventData.message)
    }

    @Test
    fun `getCapturedData should return an empty list when getHistoricalProcessExitInfo returns an empty list`() {
        // given getHistoricalProcessExitReasons returns an empty list
        records = { emptyList() }
        startApplicationExitInfoService()

        // no logs delivered
        assertTrue(args.destination.logEvents.isEmpty())
    }

    @Test
    fun `no telemetry captured when ActivityManager is unavailable`() {
        startApplicationExitInfoService(recordSourceProvider = { null })

        assertTrue(args.destination.logEvents.isEmpty())
    }

    @Test
    fun `activity manager is not retrieved until data capture is enabled`() {
        var systemServiceLookups = 0
        args = FakeInstrumentationArgs(configService = configService)
        val source = AeiDataSourceImpl(
            args,
            worker,
            {
                systemServiceLookups++
                recordSource
            },
            store,
            FakeOrdinalStore(),
        )
        assertEquals(0, systemServiceLookups)

        source.onDataCaptureEnabled()
        assertEquals(1, systemServiceLookups)
    }

    @Test
    fun `getHistoricalProcessExitInfo should truncate to 16 entries`() {
        // given getHistoricalProcessExitReasons returns more than 16 entries
        val entries = mutableListOf<AeiRecord>()
        repeat(17) {
            entries.add(aeiRecord)
        }
        records = { entries }

        startApplicationExitInfoService()

        // then captured data should only have 16 entries
        assertEquals(16, args.destination.logEvents.size)
    }

    @Test
    fun `getUnsentExitReasons should not return AEI that have already been sent`() {
        // given getHistoricalProcessExitReasons returns 3 entries, but there are 2 that have already been sent
        val appExitInfo1 = FakeAeiRecord(timestamp = 1L, pid = STATUS, traceProvider = { TRACE.byteInputStream() })
        val appExitInfo2 = FakeAeiRecord(timestamp = 2L, pid = 2, traceProvider = { TRACE.byteInputStream() })
        val appExitInfo3 = FakeAeiRecord(timestamp = 3L, pid = 3, traceProvider = { TRACE.byteInputStream() })

        val appExitInfo1Hash = "${appExitInfo1.timestamp}_${appExitInfo1.pid}"
        val appExitInfo2Hash = "${appExitInfo2.timestamp}_${appExitInfo2.pid}"

        records = { listOf(appExitInfo1, appExitInfo2, appExitInfo3) }

        store.editAndCommit {
            putStringSet("io.embrace.aeiHashCode", setOf(appExitInfo1Hash, appExitInfo2Hash))
        }

        startApplicationExitInfoService()

        // when AEI is delivered
        val attrs = getAeiLogAttrs()

        // then captured data should only have applicationExitInfo
        assertEquals("3", attrs[EmbAeiAttributes.TIMESTAMP])
    }

    @Test
    fun `invalid session id should show up in ApplicationExitInfoData sessionIdError`() {
        // given an AEI with an invalid session ID
        val invalidSessionId = "_ 1NV@lid"
        aeiRecord.processStateSummary = invalidSessionId.toByteArray()
        records = { listOf(aeiRecord) }
        startApplicationExitInfoService()

        // when AEI is delivered
        val attrs = getAeiLogAttrs()

        // then the invalid session ID message should be added to the sessionIdError
        assertEquals("invalid session ID: $invalidSessionId", attrs[EmbAeiAttributes.SESSION_ID_ERROR])
        assertEquals("", attrs[EmbAeiAttributes.AEI_SESSION_PART_ID])
    }

    @Test
    fun `aei_user_session_id is set when processStateSummary contains combined ids`() {
        aeiRecord.processStateSummary = "${SESSION_ID}_${USER_SESSION_ID}".toByteArray()
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService()

        val attrs = getAeiLogAttrs()
        assertEquals(SESSION_ID, attrs[EmbAeiAttributes.AEI_SESSION_PART_ID])
        assertEquals(USER_SESSION_ID, attrs[EmbAeiAttributes.AEI_USER_SESSION_ID])
        assertEquals("", attrs[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("", attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
    }

    @Test
    fun `aei_user_session_id is absent when processStateSummary contains only part id`() {
        aeiRecord.processStateSummary = SESSION_ID.toByteArray()
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService()

        val attrs = getAeiLogAttrs()
        assertEquals("", attrs[EmbAeiAttributes.AEI_SESSION_PART_ID])
        assertEquals("", attrs[EmbAeiAttributes.AEI_USER_SESSION_ID])
    }

    @Test
    fun `null traces won't be sent to the blob endpoint`() {
        // given an application exit info with a null trace
        aeiRecord.traceProvider = { null }
        records = { listOf(aeiRecord) }

        // when the service is started
        startApplicationExitInfoService()

        // then no logs should be sent
        assertTrue(args.destination.logEvents.isEmpty())
    }

    @Test
    fun `OOM while reading trace`() {
        // given an OOM happens when reading a trace
        aeiRecord.traceProvider = { throw OutOfMemoryError("Ouch") }
        records = { listOf(aeiRecord) }

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull("", attrs["blob"])
        assertEquals("oom: Ouch", attrs[EmbAeiAttributes.TRACE_STATUS])
    }

    @Test
    fun `IOException while reading trace`() {
        // given an IO exception happens when reading a trace
        aeiRecord.traceProvider = { throw IOException("Ouch") }
        records = { listOf(aeiRecord) }

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull(attrs["blob"])
        assertEquals("ioexception: Ouch", attrs[EmbAeiAttributes.TRACE_STATUS])
    }

    @Test
    fun `other error while reading trace`() {
        val errorMessage = "Please turn your computer screen back on."
        // given an IO exception happens when reading a trace
        aeiRecord.traceProvider = { throw IllegalMonitorStateException(errorMessage) }
        records = { listOf(aeiRecord) }

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull(attrs["blob"])
        assertEquals("error: $errorMessage", attrs[EmbAeiAttributes.TRACE_STATUS])
    }

    @Test
    fun `Truncate trace if it exceeds limit`() {
        // given a trace that exceeds the limit
        aeiRecord.traceProvider = { "a".repeat(500).byteInputStream() }

        configService.config = EmbraceConfig(aei = { AeiConfig(traceMaxLimit = { 100 }) })
        records = { listOf(aeiRecord) }

        // when the service is started
        startApplicationExitInfoService()

        // then a truncated trace should be sent
        val logEventData = args.destination.logEvents.single()
        assertEquals("a".repeat(100), logEventData.message)
    }

    @Test
    fun `Truncate native tombstone if it exceeds limit`() {
        aeiRecord.traceProvider = { "a".repeat(500).byteInputStream() }
        aeiRecord.reason = ApplicationExitInfo.REASON_CRASH_NATIVE

        configService = FakeConfigService(
            config = EmbraceConfig(
                aei = { AeiConfig(traceMaxLimit = { 100 }) },
                autoDataCapture = { AutoDataCaptureConfig(nativeCrashCaptureEnabled = { true }) },
            ),
        )
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService(versionChecker = { true })

        val logEventData = args.destination.logEvents.single()
        assertEquals("a".repeat(100), logEventData.message)
    }

    @Test
    fun `trace stream is closed after reading a text trace`() {
        val stream = CloseRecordingStream(TRACE.toByteArray())
        aeiRecord.traceProvider = { stream }
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService()

        assertEquals(TRACE, args.destination.logEvents.single().message)
        assertTrue(stream.closed)
    }

    @Test
    fun `trace stream is closed after reading a native tombstone`() {
        val stream = CloseRecordingStream(TRACE.toByteArray())
        aeiRecord.traceProvider = { stream }
        aeiRecord.reason = ApplicationExitInfo.REASON_CRASH_NATIVE
        records = { listOf(aeiRecord) }

        startApplicationExitInfoService(versionChecker = { true })

        assertTrue(stream.closed)
    }

    private class CloseRecordingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    @Test
    fun testActivityManagerException() {
        records = { throw NullPointerException() }

        // when the service is started
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        startApplicationExitInfoService(errorHandler = errorHandler)

        // no logs were sent, and the failure was tracked as an SDK error
        assertTrue(args.destination.logEvents.isEmpty())
        val sdkError = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.ProcessAeiRecords.toString(), sdkError.msg)
        assertTrue(sdkError.throwable is NullPointerException)
    }

    @Test
    fun `one object sent per payload`() {
        val entries = (0..16).map { aeiRecord }
        records = { entries }

        startApplicationExitInfoService()

        // each AEI object with a trace should be sent in a separate payload
        assertEquals(16, args.destination.logEvents.size)
    }

    @Test
    fun `disable before queued processing runs does not mark records as delivered`() {
        records = { listOf(aeiRecord) }

        val executor = BlockingScheduledExecutorService(blockingMode = true)
        args = FakeInstrumentationArgs(configService = configService)
        val source = AeiDataSourceImpl(
            args,
            BackgroundWorker(executor),
            { recordSource },
            store,
            FakeOrdinalStore(),
        )
        source.onDataCaptureEnabled()
        source.onDataCaptureDisabled()
        executor.runCurrentlyBlocked()
        assertTrue(args.destination.logEvents.isEmpty())
        assertNull(store.getStringSet("io.embrace.aeiHashCode"))
    }

    @Test
    fun `disable during processing does not persist delivered ids or ordinals`() {
        aeiRecord.reason = ApplicationExitInfo.REASON_CRASH_NATIVE
        val ordinalStore = FakeOrdinalStore()
        args = FakeInstrumentationArgs(configService = configService)
        lateinit var source: AeiDataSourceImpl
        records = {
            source.onDataCaptureDisabled()
            listOf(aeiRecord)
        }
        source = AeiDataSourceImpl(args, worker, { recordSource }, store, ordinalStore) { true }
        source.onDataCaptureEnabled()

        assertTrue(args.destination.logEvents.isEmpty())
        assertNull(store.getStringSet("io.embrace.aeiHashCode"))
        assertEquals(1, ordinalStore.incrementAndGet(Ordinal.CRASH) { 1 })
        assertEquals(1, ordinalStore.incrementAndGet(Ordinal.AEI_CRASH) { 1 })
    }

    private fun getAeiLogAttrs(): Map<String, Any> {
        val logEventData = args.destination.logEvents.single()
        assertEquals(LogSeverity.INFO, logEventData.severity)
        assertEquals(EmbType.System.Exit, logEventData.schemaType.telemetryType)
        return logEventData.schemaType.attributes()
    }
}
