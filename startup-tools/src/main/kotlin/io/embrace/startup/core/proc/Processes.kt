package io.embrace.startup.core.proc

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Run a child process to completion, capturing both streams. The one place in this tool that starts a
 * child and waits for it: `adb`, `trace_processor_shell`, `gradle` and `git` all come through here.
 *
 * Both streams are redirected to files rather than read from pipes, for two reasons that cost a
 * campaign hours when they are got wrong:
 *
 *  - **No deadlock.** Reading one pipe to EOF and only then reading the other hangs the moment the child
 *    fills the pipe nobody is draining (64 KB on Linux and macOS). `adb shell dumpsys` and a chatty
 *    `trace_processor_shell` both clear that easily. A file has no such limit, so neither side blocks.
 *  - **The timeout can actually fire.** [waitFor] is called BEFORE anything is read. Draining a pipe
 *    first means blocking in `readText()` on a child that is hung with its stdout still open, which is
 *    exactly the case a timeout exists for, and the timeout is then never reached.
 */
object Processes {

    data class Output(val exitCode: Int, val stdout: String, val stderr: String)

    /** A child that outlived its timeout and was killed. Distinguishable from an ordinary I/O failure. */
    class TimedOut(message: String) : IOException(message)

    fun run(
        command: List<String>,
        cwd: Path? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Duration = Duration.ofHours(DEFAULT_TIMEOUT_HOURS),
    ): Output {
        val out = Files.createTempFile("startup-tools-out-", ".log")
        val err = Files.createTempFile("startup-tools-err-", ".log")
        try {
            val builder = ProcessBuilder(command)
                .redirectOutput(out.toFile())
                .redirectError(err.toFile())
            cwd?.let { builder.directory(it.toFile()) }
            builder.environment().putAll(env)
            val process = builder.start()
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(KILL_GRACE_SECONDS, TimeUnit.SECONDS)
                throw TimedOut("${command.firstOrNull()} timed out after $timeout: ${command.joinToString(" ").take(CMD_CHARS)}")
            }
            return Output(process.exitValue(), text(out), text(err))
        } finally {
            Files.deleteIfExists(out)
            Files.deleteIfExists(err)
        }
    }

    /**
     * Start a child in the background with both streams appended to [output]; closing the handle stops
     * it (SIGTERM, then a forcible kill if it lingers). For long-lived readers such as `adb logcat`.
     */
    fun background(command: List<String>, output: Path): AutoCloseable {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()))
            .start()
        return AutoCloseable {
            process.destroy()
            if (!process.waitFor(BACKGROUND_STOP_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    /** Decodes with replacement: a child's output is never worth failing a campaign over. */
    private fun text(path: Path): String = String(Files.readAllBytes(path), Charsets.UTF_8)

    private const val DEFAULT_TIMEOUT_HOURS = 6L
    private const val BACKGROUND_STOP_SECONDS = 5L
    private const val KILL_GRACE_SECONDS = 5L
    private const val CMD_CHARS = 200
}
