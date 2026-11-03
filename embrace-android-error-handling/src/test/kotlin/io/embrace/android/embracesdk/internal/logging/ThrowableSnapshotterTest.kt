package io.embrace.android.embracesdk.internal.logging

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ThrowableSnapshotterTest {

    private val type = SdkErrorType.DeliverySchedulingFail
    private val frame = StackTraceElement("A", "b", "A.kt", 1)

    @Test
    fun `snapshot copies type, timestamp, class, message and frames`() {
        val exc = CustomException(arrayOf(frame), "msg")
        val snapshot = snapshot(exc, timestampMs = 5)
        assertEquals(type, snapshot.type)
        assertEquals(5, snapshot.timestampMs)
        assertEquals(ThrowableSnapshot(CustomException::class.java.name, "msg", listOf("A.b(A.kt:1)")), snapshot.top)
        assertEquals(1, snapshot.chain.size)
        assertEquals(emptyList<ThrowableSnapshot>(), snapshot.suppressed)
    }

    @Test
    fun `causes follow the thrown throwable in the chain`() {
        val root = CustomException(arrayOf(StackTraceElement("C", "d", "C.kt", 3)), message = null)
        val middle = CustomException(arrayOf(StackTraceElement("B", "c", "B.kt", 2)), "middle", root)
        val exc = CustomException(arrayOf(frame), "top", middle)
        val name = CustomException::class.java.name
        val expected = listOf(
            ThrowableSnapshot(name, "top", listOf("A.b(A.kt:1)")),
            ThrowableSnapshot(name, "middle", listOf("B.c(B.kt:2)")),
            ThrowableSnapshot(name, null, listOf("C.d(C.kt:3)")),
        )
        assertEquals(expected, snapshot(exc).chain)
    }

    @Test
    fun `causes are capped at 3`() {
        var exc: Throwable = CustomException(arrayOf(frame), "cause 5")
        for (k in 4 downTo 0) {
            exc = CustomException(arrayOf(frame), "cause $k", exc)
        }
        assertEquals(listOf("cause 0", "cause 1", "cause 2", "cause 3"), snapshot(exc).chain.map { it.message })
    }

    @Test
    fun `suppressed exceptions are captured separately from causes`() {
        val cause = CustomException(arrayOf(frame), "cause")
        val exc = CustomException(arrayOf(frame), "top", cause)
        exc.addSuppressed(CustomException(arrayOf(StackTraceElement("C", "d", "C.kt", 3)), "suppressed"))
        val snapshot = snapshot(exc)
        assertEquals(listOf("top", "cause"), snapshot.chain.map { it.message })
        assertEquals(
            listOf(ThrowableSnapshot(CustomException::class.java.name, "suppressed", listOf("C.d(C.kt:3)"))),
            snapshot.suppressed,
        )
    }

    @Test
    fun `suppressed exceptions are capped at 3`() {
        val exc = CustomException(arrayOf(frame))
        repeat(5) { exc.addSuppressed(CustomException(arrayOf(frame), "suppressed $it")) }
        assertEquals(listOf("suppressed 0", "suppressed 1", "suppressed 2"), snapshot(exc).suppressed.map { it.message })
    }

    @Test
    fun `a throwing message is replaced with a placeholder`() {
        val exc = object : RuntimeException() {
            override val message: String get() = error("message")
        }
        assertEquals("<unavailable: message>", snapshot(exc).top.message)
    }

    @Test
    fun `a throwing stack trace is replaced with a placeholder`() {
        val exc = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement> = error("stackTrace")
        }
        assertEquals(listOf("<unavailable: stackTrace>"), snapshot(exc).top.frames)
    }

    @Test
    fun `a throwing cause ends the chain`() {
        val exc = object : RuntimeException("top") {
            override val cause: Throwable get() = error("cause")
        }
        assertEquals(listOf("top"), snapshot(exc).chain.map { it.message })
    }

    @Test
    fun `a null stack trace has no frames`() {
        val exc = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement>? = null
        }
        assertEquals(emptyList<String>(), snapshot(exc).top.frames)
    }

    @Test
    fun `null frames are replaced with a placeholder`() {
        val exc = object : RuntimeException() {
            override fun getStackTrace(): Array<StackTraceElement?> = arrayOf(frame, null)
        }
        assertEquals(listOf("A.b(A.kt:1)", "<unavailable: frame>"), snapshot(exc).top.frames)
    }

    @Test
    fun `a throwable that is its own cause appears once`() {
        val exc = CyclicException("self")
        exc.next = exc
        assertEquals(listOf("self"), snapshot(exc).chain.map { it.message })
    }

    @Test
    fun `an A-B-A cause cycle stops after B`() {
        val a = CyclicException("a")
        val b = CyclicException("b", a)
        a.next = b
        assertEquals(listOf("a", "b"), snapshot(a).chain.map { it.message })
    }

    @Test
    fun `a suppressed exception that is its own cause is snapshotted once`() {
        val suppressed = CyclicException("suppressed")
        suppressed.next = suppressed
        val exc = CustomException(arrayOf(frame), "top")
        exc.addSuppressed(suppressed)
        val snapshot = snapshot(exc)
        assertEquals(listOf("top"), snapshot.chain.map { it.message })
        assertEquals(listOf("suppressed"), snapshot.suppressed.map { it.message })
    }

    @Test
    fun `a suppressed exception already in the cause chain is skipped`() {
        val cause = CustomException(arrayOf(frame), "cause")
        val exc = CustomException(arrayOf(frame), "top", cause)
        exc.addSuppressed(cause)
        assertEquals(emptyList<ThrowableSnapshot>(), snapshot(exc).suppressed)
    }

    @Test
    fun `frames are capped at 200`() {
        val exc = CustomException(Array(10_000) { frame })
        assertEquals(200, snapshot(exc).top.frames.size)
    }

    @Test
    fun `messages are capped at 1024 chars`() {
        val exc = CustomException(arrayOf(frame), "x".repeat(1024 * 1024))
        assertEquals("x".repeat(1024), snapshot(exc).top.message)
    }

    @Test
    fun `equals and hashCode are never called`() {
        val cause = HostileException("cause")
        val exc = HostileException("top", cause)
        exc.addSuppressed(HostileException("suppressed"))
        val snapshot = snapshot(exc)
        assertEquals(listOf("top", "cause"), snapshot.chain.map { it.message })
        assertEquals(listOf("suppressed"), snapshot.suppressed.map { it.message })
    }

    private fun snapshot(exc: Throwable, timestampMs: Long = 0) = ThrowableSnapshotter.snapshot(type, exc, timestampMs)

    private class CustomException(
        private val customStacktrace: Array<StackTraceElement>,
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        override fun getStackTrace(): Array<StackTraceElement> = customStacktrace
    }

    private class CyclicException(message: String, var next: Throwable? = null) : RuntimeException(message) {
        override val cause: Throwable?
            get() = next
    }

    private class HostileException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
        override fun equals(other: Any?): Boolean = error("equals")
        override fun hashCode(): Int = error("hashCode")
    }
}
