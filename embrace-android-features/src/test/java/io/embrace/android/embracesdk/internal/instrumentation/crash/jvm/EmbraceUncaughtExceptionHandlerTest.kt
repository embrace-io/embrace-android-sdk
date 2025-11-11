package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.fakes.FakeJvmCrashDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that the [EmbraceUncaughtExceptionHandler]:
 *
 *  * Does not permit null args
 *  * Delegates to the [JvmCrashDataSource]
 *  * Always delegates to the default [Thread.UncaughtExceptionHandler]
 */
internal class EmbraceUncaughtExceptionHandlerTest {

    /**
     * Tests that [EmbraceUncaughtExceptionHandler] accepts a null arg.
     *
     *
     * The [Thread.UncaughtExceptionHandler] is null by default.
     */
    @Test
    fun testNullArg1() {
        EmbraceUncaughtExceptionHandler(null, FakeJvmCrashDataSource(), EmbLoggerImpl())
    }

    /**
     * Tests that [EmbraceUncaughtExceptionHandler] correctly returns the crash to the
     * [JvmCrashDataSource], and then delegates to the default [Thread.UncaughtExceptionHandler].
     */
    @Test
    fun testExceptionHandler() {
        val defaultHandler = TestUncaughtExceptionHandler()
        val fakeCrashService = FakeJvmCrashDataSource()
        val handler = EmbraceUncaughtExceptionHandler(defaultHandler, fakeCrashService, EmbLoggerImpl())

        val testException = RuntimeException("Test exception")
        handler.uncaughtException(Thread.currentThread(), testException)
        val actual = fakeCrashService.exception

        // Test that the exception returned to the crash service matches
        assertEquals(testException, actual)

        // Test that the exception was successfully delegated to the default handler
        assertEquals(Thread.currentThread(), defaultHandler.thread)
        assertEquals(testException, defaultHandler.throwable)
    }

    /**
     * Tests that where we throw an exception whilst processing the crash, we still delegate
     * to the default [Thread.UncaughtExceptionHandler].
     */
    @Test
    fun testCrashingExceptionHandler() {
        val defaultHandler = TestUncaughtExceptionHandler()
        val crashingCrashService = CrashingJvmCrashService()
        val handler = EmbraceUncaughtExceptionHandler(defaultHandler, crashingCrashService, EmbLoggerImpl())
        val testException = RuntimeException("Test exception")
        handler.uncaughtException(Thread.currentThread(), testException)

        // Test that the exception was successfully delegated to the default handler
        assertEquals(Thread.currentThread(), defaultHandler.thread)
        assertEquals(testException, defaultHandler.throwable)
    }

    internal class CrashingJvmCrashService : JvmCrashDataSource {
        override fun logUnhandledJvmThrowable(exception: Throwable) {
            throw RuntimeException("Test crash")
        }

        override var telemetryModifier: ((TelemetryAttributes) -> SchemaType)? = null

        override fun addCrashTeardownHandler(handler: CrashTeardownHandler) {
        }

        override fun onDataCaptureEnabled() {
        }

        override fun onDataCaptureDisabled() {
        }

        override fun resetDataCaptureLimits() {
        }

        override fun <T> captureTelemetry(
            inputValidation: () -> Boolean,
            action: TelemetryDestination.() -> T?,
        ): T? = null
    }

    internal class TestUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
        var thread: Thread? = null
        var throwable: Throwable? = null

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            this.thread = thread
            this.throwable = throwable
        }
    }
}
