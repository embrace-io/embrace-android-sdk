package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeLogAction
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class InternalErrorServiceActionTest {

    private val exception = Exception()
    private val errorMsg = "Error message"
    private lateinit var internalErrorService: InternalErrorService
    private lateinit var reportingLoggerAction: InternalErrorServiceAction
    private lateinit var loggerAction: FakeLogAction

    private fun setupService() {
        internalErrorService = mockk(relaxUnitFun = true)
        reportingLoggerAction = InternalErrorServiceAction(internalErrorService)
    }

    @Before
    fun setUp() {
        loggerAction = FakeLogAction()
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

        verify(exactly = 1) { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if an exception is thrown reporting error, swallow it`() {
        setupService()
        every { internalErrorService.handleInternalError(exception) } throws RuntimeException()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.DEBUG, exception, true)
        verify(exactly = 1) { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if a throwable is not available with INFO severity then dont handle exception`() {
        setupService()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.INFO, null, true)
        verify(exactly = 0) { internalErrorService.handleInternalError(any() as Exception) }
    }

    @Test
    fun `if a throwable is available with ERROR severity`() {
        setupService()
        reportingLoggerAction.log(errorMsg, InternalEmbraceLogger.Severity.ERROR, exception, true)
        verify(exactly = 1) { internalErrorService.handleInternalError(exception) }
    }
}
