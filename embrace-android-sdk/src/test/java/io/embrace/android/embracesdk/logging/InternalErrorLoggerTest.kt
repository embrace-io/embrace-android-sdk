package io.embrace.android.embracesdk.logging

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.BeforeClass
import org.junit.Test

internal class InternalErrorLoggerTest {

    private lateinit var internalErrorLogger: InternalErrorLogger

    companion object {
        private lateinit var mockExceptionService: EmbraceInternalErrorService
        private lateinit var mockLogger: InternalEmbraceLogger.LoggerAction

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockExceptionService = mockk(relaxUnitFun = true)
            mockLogger = mockk(relaxUnitFun = true)
        }
    }

    private fun setupService(strictModeEnabled: Boolean = false) {
        internalErrorLogger =
            InternalErrorLogger(mockExceptionService, mockLogger, strictModeEnabled)
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `if no throwable then do not handle exception`() {
        setupService()
        internalErrorLogger.log("message", InternalStaticEmbraceLogger.Severity.DEBUG, null, true)

        verify { mockExceptionService wasNot Called }
    }

    @Test
    fun `if throwable available, then do handle exception`() {
        setupService()
        val exception = Exception()
        internalErrorLogger.log("message", InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { mockExceptionService.handleInternalError(exception) }
    }

    @Test
    fun `if an exception is thrown while handling exception, then log it and dont rethrow it`() {
        setupService()
        val exceptionMessage = "root cause"
        val exception = Exception()
        val msg = "message"
        every { mockExceptionService.handleInternalError(exception) } throws RuntimeException(
            exceptionMessage
        )

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { mockExceptionService.handleInternalError(exception) }
        verify { mockLogger.log(exceptionMessage, InternalStaticEmbraceLogger.Severity.ERROR, null, false) }
    }

    @Test
    fun `if an exception is thrown while handling exception, then log it and dont rethrow it with no root cause message`() {
        setupService()
        val exception = Exception()
        val msg = "message"
        every { mockExceptionService.handleInternalError(exception) } throws RuntimeException()

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { mockExceptionService.handleInternalError(exception) }
        verify { mockLogger.log("", InternalStaticEmbraceLogger.Severity.ERROR, null, false) }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is available with ERROR severity`() {
        setupService(true)
        val exception = Exception()
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, exception, true)

        verify { mockExceptionService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with ERROR severity then handle exception`() {
        setupService(true)
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, null, true)

        verify(exactly = 1) {
            mockExceptionService.handleInternalError(
                any() as InternalErrorLogger.LogStrictModeException
            )
        }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with INFO severity then dont handle exception`() {
        setupService(true)
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.INFO, null, true)

        verify(exactly = 0) { mockExceptionService.handleInternalError(any() as Exception) }
    }

    @Test
    fun `if logStrictMode is disabled and a throwable is available with ERROR severity`() {
        setupService(false)
        val exception = Exception()
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, exception, true)

        verify { mockExceptionService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and an exception is thrown while handling exception, then log it and dont rethrow it`() {
        setupService(true)
        val exceptionMessage = "root cause"
        val exception = Exception()
        val msg = "message"
        every { mockExceptionService.handleInternalError(any() as InternalErrorLogger.LogStrictModeException) } throws RuntimeException(
            exceptionMessage
        )

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { mockExceptionService.handleInternalError(any() as InternalErrorLogger.LogStrictModeException) }
        verify { mockLogger.log(exceptionMessage, InternalStaticEmbraceLogger.Severity.ERROR, null, false) }
    }
}
