package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class ReportingLoggerActionTest {

    private val exception = Exception()
    private val errorMsg = "Error message"
    private lateinit var internalErrorService: InternalErrorService
    private lateinit var reportingLoggerAction: ReportingLoggerAction
    private lateinit var loggerAction: FakeLoggerAction

    private fun setupService(strictModeEnabled: Boolean = false) {
        internalErrorService = mockk(relaxUnitFun = true)
        reportingLoggerAction = ReportingLoggerAction(internalErrorService, strictModeEnabled)
    }

    @Before
    fun setUp() {
        loggerAction = FakeLoggerAction()
    }

    @Test
    fun `do not report error if no throwable available`() {
        setupService()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.DEBUG, null, true)

        verify { internalErrorService wasNot Called }
    }

    @Test
    fun `report error if throwable available`() {
        setupService()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.DEBUG, exception, true)

        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if an exception is thrown reporting error, swallow it`() {
        setupService()
        every { internalErrorService.handleInternalError(exception) } throws RuntimeException()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.DEBUG, exception, true)
        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is available with ERROR severity`() {
        setupService(true)
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.ERROR, exception, true)
        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with ERROR severity then handle exception`() {
        setupService(true)
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.ERROR, null, true)
        verify(exactly = 1) {
            internalErrorService.handleInternalError(
                any() as ReportingLoggerAction.LogStrictModeException
            )
        }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with INFO severity then dont handle exception`() {
        setupService(true)
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.INFO, null, true)
        verify(exactly = 0) { internalErrorService.handleInternalError(any() as Exception) }
    }

    @Test
    fun `if logStrictMode is disabled and a throwable is available with ERROR severity`() {
        setupService(false)
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.ERROR, exception, true)
        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and an exception is thrown, swallow it`() {
        setupService(true)
        every {
            internalErrorService.handleInternalError(any() as ReportingLoggerAction.LogStrictModeException)
        } throws RuntimeException()

        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.DEBUG, exception, true)
        verify { internalErrorService.handleInternalError(any() as ReportingLoggerAction.LogStrictModeException) }
    }
}
