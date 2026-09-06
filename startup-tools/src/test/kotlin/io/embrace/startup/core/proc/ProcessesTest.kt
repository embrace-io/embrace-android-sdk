package io.embrace.startup.core.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * The two failure modes this helper exists to prevent. Both tests hang forever against the
 * read-a-pipe-then-wait implementation this replaced, so each carries its own JUnit timeout: a hang here
 * must fail the suite rather than stall it, which is the whole point of the change.
 */
class ProcessesTest {

    /**
     * A child that writes more than a pipe buffer to stderr BEFORE writing stdout. Draining stdout first
     * deadlocks: the child blocks writing stderr, the parent blocks reading stdout, neither moves.
     */
    @Test(timeout = HANG_GUARD_MS)
    fun `a child that fills both streams is captured in full`() {
        val script = "dd if=/dev/zero bs=1024 count=$KB 2>/dev/null | tr '\\0' 'E' >&2; " +
            "dd if=/dev/zero bs=1024 count=$KB 2>/dev/null | tr '\\0' 'O'"

        val out = Processes.run(listOf("/bin/sh", "-c", script), timeout = Duration.ofSeconds(SHORT_S))

        assertEquals(0, out.exitCode)
        assertEquals("stdout captured in full", KB * BYTES_PER_KB, out.stdout.length)
        assertEquals("stderr captured in full", KB * BYTES_PER_KB, out.stderr.length)
        assertTrue(out.stdout.all { it == 'O' })
        assertTrue(out.stderr.all { it == 'E' })
    }

    /**
     * A child that hangs with stdout still open, which is what a wedged `adb shell` looks like. Waiting
     * only after reading stdout means the timeout is never reached and the campaign stalls for hours.
     */
    @Test(timeout = HANG_GUARD_MS)
    fun `a hung child is killed when its timeout expires`() {
        val started = System.nanoTime()

        val error = runCatching {
            Processes.run(listOf("/bin/sh", "-c", "sleep $SLEEP_S"), timeout = Duration.ofSeconds(1))
        }.exceptionOrNull()

        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        assertTrue("expected a timeout, got $error", error is Processes.TimedOut)
        assertTrue("returned in $elapsed, long before the child would exit", elapsed.seconds < SLEEP_S / 2)
        assertTrue(error!!.message!!.contains("timed out"))
    }

    @Test(timeout = HANG_GUARD_MS)
    fun `a non-zero exit is returned rather than thrown, with the streams kept apart`() {
        val out = Processes.run(
            listOf("/bin/sh", "-c", "echo out; echo err >&2; exit 3"),
            timeout = Duration.ofSeconds(SHORT_S),
        )

        assertEquals(3, out.exitCode)
        assertEquals("out\n", out.stdout)
        assertEquals("err\n", out.stderr)
    }

    private companion object {
        const val HANG_GUARD_MS = 60_000L
        const val KB = 256
        const val BYTES_PER_KB = 1024
        const val SHORT_S = 30L
        const val SLEEP_S = 60L
    }
}
