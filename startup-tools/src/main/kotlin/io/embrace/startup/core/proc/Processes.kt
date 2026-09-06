package io.embrace.startup.core.proc

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Run a child process to completion, capturing both streams; a timeout kills it and throws. */
object Processes {

    data class Output(val exitCode: Int, val stdout: String, val stderr: String)

    fun run(
        command: List<String>,
        cwd: Path? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Duration = Duration.ofHours(DEFAULT_TIMEOUT_HOURS),
    ): Output {
        val builder = ProcessBuilder(command).redirectErrorStream(false)
        cwd?.let { builder.directory(it.toFile()) }
        builder.environment().putAll(env)
        val process = builder.start()
        // Drain stderr on its own thread so a chatty child cannot deadlock on a full pipe.
        val stderr = StringBuilder()
        val drain = Thread { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } }
        drain.start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IOException("${command.firstOrNull()} timed out after $timeout")
        }
        drain.join()
        return Output(process.exitValue(), stdout, stderr.toString())
    }

    private const val DEFAULT_TIMEOUT_HOURS = 6L
}
