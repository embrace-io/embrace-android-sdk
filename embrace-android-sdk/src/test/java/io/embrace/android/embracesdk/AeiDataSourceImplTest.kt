package io.embrace.android.embracesdk

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.aei.AeiDataSourceImpl
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeAppExitInfoBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

internal class AeiDataSourceImplTest {

    private lateinit var applicationExitInfoService: AeiDataSourceImpl
    private lateinit var logWriter: FakeLogWriter

    private val worker = BackgroundWorker(MoreExecutors.newDirectExecutorService())

    private var appExitInfoConfig = AppExitInfoConfig(pctAeiCaptureEnabled = 100.0f)
    private val configService = FakeConfigService(
        appExitInfoBehavior = fakeAppExitInfoBehavior {
            RemoteConfig(appExitInfoConfig = appExitInfoConfig)
        }
    )

    private val preferenceService = FakePreferenceService()

    private val mockActivityManager: ActivityManager = mockk {
        every { getHistoricalProcessExitReasons(any(), any(), any()) } returns emptyList()
    }

    private val mockAppExitInfo = mockk<ApplicationExitInfo>(relaxed = true) {
        every { timestamp } returns TIMESTAMP
        every { pid } returns PID
        every { processStateSummary } returns SESSION_ID.toByteArray()
        every { importance } returns IMPORTANCE
        every { pss } returns PSS
        every { reason } returns REASON
        every { rss } returns RSS
        every { status } returns STATUS
        every { description } returns DESCRIPTION
        every { traceInputStream } returns TRACE.byteInputStream()
    }

    companion object {
        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private fun startApplicationExitInfoService() {
        logWriter = FakeLogWriter()
        applicationExitInfoService = AeiDataSourceImpl(
            worker,
            configService.appExitInfoBehavior,
            mockActivityManager,
            preferenceService,
            logWriter,
            EmbLoggerImpl()
        ).apply(AeiDataSourceImpl::enableDataCapture)
    }

    @Test
    fun `AEI data capture happy path`() {
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        startApplicationExitInfoService()

        // when getCapturedData is called
        val attrs = getAeiLogAttrs()
        assertEquals(TIMESTAMP.toString(), attrs["timestamp"])
        assertEquals(SESSION_ID, attrs["aei_session_id"])
        assertEquals(IMPORTANCE.toString(), attrs["process_importance"])
        assertEquals(PSS.toString(), attrs["pss"])
        assertEquals(RSS.toString(), attrs["rss"])
        assertEquals(STATUS.toString(), attrs["exit_status"])
        assertEquals(DESCRIPTION, attrs["description"])
        assertEquals("", attrs["session_id_error"])
        assertNull(attrs["trace_status"])

        val logEventData = logWriter.logEvents.single()
        assertEquals("testInputStream", logEventData.message)
    }

    @Test
    fun `getCapturedData should return an empty list when getHistoricalProcessExitInfo returns an empty list`() {
        // given getHistoricalProcessExitReasons returns an empty list
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns emptyList()
        startApplicationExitInfoService()

        // no logs delivered
        assertTrue(logWriter.logEvents.isEmpty())
    }

    @Test
    fun `getHistoricalProcessExitInfo should truncate to 32 entries`() {
        // given getHistoricalProcessExitReasons returns more than 32 entries
        val appExitInfoListWithMoreThan32Entries = mutableListOf<ApplicationExitInfo>()
        repeat(33) {
            appExitInfoListWithMoreThan32Entries.add(mockAppExitInfo)
        }
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns appExitInfoListWithMoreThan32Entries

        startApplicationExitInfoService()

        // then captured data should only have 32 entries
        assertEquals(32, logWriter.logEvents.size)
    }

    @Test
    fun `getUnsentExitReasons should not return AEI that have already been sent`() {
        // given getHistoricalProcessExitReasons returns 3 entries, but there are 2 that have already been sent
        val appExitInfo1 = mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns 1L
            every { pid } returns STATUS
        }
        val appExitInfo2 = mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns 2L
            every { pid } returns 2
        }
        val appExitInfo3 = mockk<ApplicationExitInfo>(relaxed = true) {
            every { timestamp } returns 3L
            every { pid } returns 3
        }

        val appExitInfo1Hash = "${appExitInfo1.timestamp}_${appExitInfo1.pid}"
        val appExitInfo2Hash = "${appExitInfo2.timestamp}_${appExitInfo2.pid}"

        every { mockActivityManager.getHistoricalProcessExitReasons(any(), any(), any()) } returns
            listOf(appExitInfo1, appExitInfo2, appExitInfo3)

        preferenceService.applicationExitInfoHistory = setOf(
            appExitInfo1Hash,
            appExitInfo2Hash
        )

        startApplicationExitInfoService()

        // when AEI is delivered
        val attrs = getAeiLogAttrs()

        // then captured data should only have applicationExitInfo3
        assertEquals("3", attrs["timestamp"])
    }

    @Test
    fun `invalid session id should show up in ApplicationExitInfoData sessionIdError`() {
        // given an AEI with an invalid session ID
        val invalidSessionId = "_ 1NV@lid"
        every { mockAppExitInfo.processStateSummary } returns invalidSessionId.toByteArray()
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)
        startApplicationExitInfoService()

        // when AEI is delivered
        val attrs = getAeiLogAttrs()

        // then the invalid session ID message should be added to the sessionIdError
        assertEquals("invalid session ID: $invalidSessionId", attrs["session_id_error"])
        assertEquals(invalidSessionId, attrs["aei_session_id"])
    }

    @Test
    fun `null traces won't be sent to the blob endpoint`() {
        // given an application exit info with a null trace
        every { mockAppExitInfo.traceInputStream } returns null
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        // when the service is started
        startApplicationExitInfoService()

        // then no logs should be sent
        assertTrue(logWriter.logEvents.isEmpty())
    }

    @Test
    fun `OOM while reading trace`() {
        // given an OOM happens when reading a trace
        every { mockAppExitInfo.traceInputStream } throws OutOfMemoryError("Ouch")
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull("", attrs["blob"])
        assertEquals("oom: Ouch", attrs["trace_status"])
    }

    @Test
    fun `IOException while reading trace`() {
        // given an IO exception happens when reading a trace
        every { mockAppExitInfo.traceInputStream } throws IOException("Ouch")
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull(attrs["blob"])
        assertEquals("ioexception: Ouch", attrs["trace_status"])
    }

    @Test
    fun `other error while reading trace`() {
        val errorMessage = "Please turn your computer screen back on."
        // given an IO exception happens when reading a trace
        every { mockAppExitInfo.traceInputStream } throws IllegalMonitorStateException(errorMessage)
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        // when the service is started
        startApplicationExitInfoService()

        // then a null trace should be sent
        val attrs = getAeiLogAttrs()
        assertNull(attrs["blob"])
        assertEquals("error: $errorMessage", attrs["trace_status"])
    }

    @Test
    fun `Truncate trace if it exceeds limit`() {
        // given a trace that exceeds the limit
        every { mockAppExitInfo.traceInputStream } returns "a".repeat(500).byteInputStream()

        appExitInfoConfig =
            AppExitInfoConfig(pctAeiCaptureEnabled = 100.0f, appExitInfoTracesLimit = 100)
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns listOf(mockAppExitInfo)

        // when the service is started
        startApplicationExitInfoService()

        // then a truncated trace should be sent
        val logEventData = logWriter.logEvents.single()
        assertEquals("a".repeat(100), logEventData.message)
    }

    @Test
    fun testActivityManagerException() {
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } throws NullPointerException()

        // when the service is started
        startApplicationExitInfoService()

        // no logs were sent
        assertTrue(logWriter.logEvents.isEmpty())
    }

    @Test
    fun `one object sent per payload`() {
        val entries = (0..32).map { mockAppExitInfo }
        every {
            mockActivityManager.getHistoricalProcessExitReasons(
                any(),
                any(),
                any()
            )
        } returns entries

        startApplicationExitInfoService()

        // each AEI object with a trace should be sent in a separate payload
        assertEquals(32, logWriter.logEvents.size)
    }

    private fun getAeiLogAttrs(): Map<String, String> {
        val logEventData = logWriter.logEvents.single()
        assertEquals(EmbType.System.Exit, logEventData.schemaType.telemetryType)
        assertEquals("", logEventData.schemaType.fixedObjectName)
        assertEquals(Severity.INFO, logEventData.severity)

        return logEventData.schemaType.attributes()
    }
}
