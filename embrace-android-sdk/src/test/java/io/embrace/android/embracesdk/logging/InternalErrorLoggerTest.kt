package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class InternalErrorLoggerTest {

    private lateinit var internalErrorLogger: InternalErrorLogger
    private lateinit var loggerAction: FakeLoggerAction

    companion object {
        private lateinit var internalErrorService: InternalErrorService

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            internalErrorService = mockk(relaxUnitFun = true)
        }
    }

    private fun setupService(strictModeEnabled: Boolean = false) {
        internalErrorLogger =
            InternalErrorLogger(internalErrorService, loggerAction, strictModeEnabled)
    }

    @Before
    fun setUp() {
        loggerAction = FakeLoggerAction()
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `if no throwable then do not handle exception`() {
        setupService()
        internalErrorLogger.log("message", InternalStaticEmbraceLogger.Severity.DEBUG, null, true)

        verify { internalErrorService wasNot Called }
    }

    @Test
    fun `if throwable available, then do handle exception`() {
        setupService()
        val exception = Exception()
        internalErrorLogger.log("message", InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if an exception is thrown while handling exception, then log it and dont rethrow it`() {
        setupService()
        val exceptionMessage = "root cause"
        val exception = Exception()
        val msg = "message"
        every { internalErrorService.handleInternalError(exception) } throws RuntimeException(
            exceptionMessage
        )

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { internalErrorService.handleInternalError(exception) }
        loggerAction.msgQueue.single {
            it.msg == exceptionMessage && it.severity == InternalStaticEmbraceLogger.Severity.ERROR &&
                it.throwable == null && !it.logStacktrace
        }
    }

    @Test
    fun `if an exception is thrown while handling exception, then log it and dont rethrow it with no root cause message`() {
        setupService()
        val exception = Exception()
        val msg = "message"
        every { internalErrorService.handleInternalError(exception) } throws RuntimeException()

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { internalErrorService.handleInternalError(exception) }

        loggerAction.msgQueue.single {
            it.msg == "" && it.severity == InternalStaticEmbraceLogger.Severity.ERROR &&
                it.throwable == null && !it.logStacktrace
        }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is available with ERROR severity`() {
        setupService(true)
        val exception = Exception()
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, exception, true)

        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with ERROR severity then handle exception`() {
        setupService(true)
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, null, true)

        verify(exactly = 1) {
            internalErrorService.handleInternalError(
                any() as InternalErrorLogger.LogStrictModeException
            )
        }
    }

    @Test
    fun `if logStrictMode is enabled and a throwable is not available with INFO severity then dont handle exception`() {
        setupService(true)
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.INFO, null, true)

        verify(exactly = 0) { internalErrorService.handleInternalError(any() as Exception) }
    }

    @Test
    fun `if logStrictMode is disabled and a throwable is available with ERROR severity`() {
        setupService(false)
        val exception = Exception()
        val errorMsg = "Error message"
        internalErrorLogger.log(errorMsg, InternalStaticEmbraceLogger.Severity.ERROR, exception, true)

        verify { internalErrorService.handleInternalError(exception) }
    }

    @Test
    fun `if logStrictMode is enabled and an exception is thrown while handling exception, then log it and dont rethrow it`() {
        setupService(true)
        val exceptionMessage = "root cause"
        val exception = Exception()
        val msg = "message"
        every { internalErrorService.handleInternalError(any() as InternalErrorLogger.LogStrictModeException) } throws RuntimeException(
            exceptionMessage
        )

        internalErrorLogger.log(msg, InternalStaticEmbraceLogger.Severity.DEBUG, exception, true)

        verify { internalErrorService.handleInternalError(any() as InternalErrorLogger.LogStrictModeException) }

        loggerAction.msgQueue.single {
            it.msg == exceptionMessage && it.severity == InternalStaticEmbraceLogger.Severity.ERROR &&
                it.throwable == null && !it.logStacktrace
        }
    }
}
