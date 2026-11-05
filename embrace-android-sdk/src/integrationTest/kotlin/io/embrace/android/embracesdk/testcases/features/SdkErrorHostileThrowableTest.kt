package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Throws hostile [Throwable]s from SDK code guarded by [guardSdkCode] and asserts that each reaches the `sys.internal` log
 * intact or with placeholders, and never escapes to the caller.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class SdkErrorHostileThrowableTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `message that throws is replaced with a placeholder`() {
        val exc = object : RuntimeException() {
            override val message: String
                get() = throw IllegalStateException("message")
        }
        verifyHostileThrowable(exc) { attrs ->
            assertEquals("<unavailable: message>", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
        }
    }

    @Test
    fun `toString that throws is never called`() {
        val exc = object : RuntimeException("toString throws") {
            override fun toString(): String = throw IllegalStateException("toString")
        }
        verifyHostileThrowable(exc) { attrs ->
            assertEquals("toString throws", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
            assertTrue(attrs.stacktraceLines().isNotEmpty())
        }
    }

    @Test
    fun `stackTrace that throws is replaced with a placeholder`() {
        val exc = object : RuntimeException("stackTrace throws") {
            override fun getStackTrace(): Array<StackTraceElement> = throw IllegalStateException("stackTrace")
        }
        verifyHostileThrowable(exc) { attrs ->
            assertEquals(listOf("<unavailable: stackTrace>"), attrs.stacktraceLines())
        }
    }

    @Test
    fun `null stack trace is sent without frames`() {
        val exc = object : RuntimeException("null stackTrace") {
            @Suppress("WRONG_TYPE_FOR_JAVA_OVERRIDE")
            override fun getStackTrace(): Array<StackTraceElement>? = null
        }
        verifyHostileThrowable(exc) { attrs ->
            assertEquals(emptyList<String>(), attrs.stacktraceLines())
        }
    }

    @Test
    fun `cyclic causes and suppressed exceptions are sent`() {
        val exc = RuntimeException("cycle top")
        val cause = RuntimeException("cycle cause", exc)
        exc.initCause(cause)
        exc.addSuppressed(cause)
        cause.addSuppressed(exc)
        verifyHostileThrowable(exc) { attrs ->
            assertEquals("cycle top", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
        }
    }

    @Test
    fun `huge stack trace is capped`() {
        val exc = RuntimeException("huge trace").apply {
            stackTrace = Array(10_000) { k -> StackTraceElement("Hostile", "frame$k", "Hostile.kt", k) }
        }
        verifyHostileThrowable(exc) { attrs ->
            val lines = attrs.stacktraceLines()
            assertEquals(200, lines.size)
            assertEquals("Hostile.frame0(Hostile.kt:0)", lines.first())
        }
    }

    @Test
    fun `huge frame strings are capped`() {
        val huge = "a".repeat(1024 * 1024)
        val exc = RuntimeException("huge frames").apply {
            stackTrace = Array(200) { StackTraceElement(huge, "b", "Hostile.kt", 1) }
        }
        verifyHostileThrowable(exc) { attrs ->
            val lines = attrs.stacktraceLines()
            assertEquals(200, lines.size)
            assertTrue(lines.all { it == "a".repeat(512) })
        }
    }

    @Test
    fun `huge message with control chars and lone surrogates is capped`() {
        val unit = "a\u0000\u0007\n\r\t\u001b\uD800b\uDC00"
        val message = unit.repeat(1024 * 1024 / unit.length)
        val exc = RuntimeException(message)
        verifyHostileThrowable(exc) { attrs ->
            val sent = checkNotNull(attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
            assertTrue("message length ${sent.length}", sent.isNotEmpty() && sent.length <= 1024)
        }
    }

    @Test
    fun `OutOfMemoryError is sent`() {
        verifyHostileThrowable(OutOfMemoryError("Java heap space")) { attrs ->
            assertEquals("Java heap space", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
            assertTrue(attrs.stacktraceLines().size <= 32)
        }
    }

    @Test
    fun `StackOverflowError is sent with a capped trace`() {
        verifyHostileThrowable(
            expectedType = StackOverflowError::class.java.name,
            action = { recurse(0) },
        ) { attrs ->
            val lines = attrs.stacktraceLines()
            assertTrue("${lines.size} frames", lines.isNotEmpty() && lines.size <= 32)
        }
    }

    @Test
    fun `InterruptedException keeps the interrupt status`() {
        var interrupted = false
        verifyHostileThrowable(
            expectedType = InterruptedException::class.java.name,
            action = { throw InterruptedException("interrupted") },
            afterAction = { interrupted = Thread.interrupted() },
        ) { attrs ->
            assertTrue(interrupted)
            assertEquals("interrupted", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
        }
    }

    @Test
    fun `equals and hashCode that throw are never called`() {
        val exc = object : RuntimeException("equality throws") {
            override fun equals(other: Any?): Boolean = throw IllegalStateException("equals")
            override fun hashCode(): Int = throw IllegalStateException("hashCode")
        }
        verifyHostileThrowable(exc) { attrs ->
            assertEquals("equality throws", attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE))
        }
    }

    private fun verifyHostileThrowable(exc: Throwable, assertAttrs: (List<Attribute>) -> Unit) {
        verifyHostileThrowable(exc.javaClass.name, { throw exc }, assertAttrs = assertAttrs)
    }

    private fun verifyHostileThrowable(
        expectedType: String,
        action: () -> Unit,
        afterAction: () -> Unit = {},
        assertAttrs: (List<Attribute>) -> Unit,
    ) {
        lateinit var errorHandler: ErrorHandler
        testRule.runTest(
            setupAction = {
                getFakeErrorHandler().throwOnSdkError = false
                errorHandler = getFakeErrorHandler()
            },
            testCaseAction = {
                recordSession {
                    errorHandler.guardSdkCode(SdkErrorType.InternalInterfaceFail, action)
                    afterAction()
                }
            },
            assertAction = {
                val attrs = sdkErrorLogAttributes()
                assertEquals(expectedType, attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_TYPE))
                assertTrue(attrs.stacktraceLines().size <= 200)
                assertAttrs(attrs)
            }
        )
    }

    private fun EmbracePayloadAssertionInterface.sdkErrorLogAttributes(): List<Attribute> {
        val log = getSingleLogEnvelope().getLogOfType(EmbType.System.SdkError)
        assertEquals("ERROR", log.severityText)
        return checkNotNull(log.attributes)
    }

    private fun List<Attribute>.stacktraceLines(): List<String> {
        val trace = findAttributeValue(ExceptionAttributes.EXCEPTION_STACKTRACE)
        return if (trace.isNullOrEmpty()) emptyList() else trace.lines()
    }

    private fun recurse(depth: Int): Int = recurse(depth + 1) + 1

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
