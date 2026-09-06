package io.embrace.startup.device

import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The thinnest useful wrapper over the `adb` binary: run a command against one serial (or the only
 * device), capture stdout, never throw on a non-zero exit - callers decide what an empty answer means,
 * exactly as the Python scripts' `adb()` helpers did. A timeout kills the process and surfaces as an
 * [IOException], because a hung `adb shell` is the classic way a campaign stalls for hours.
 */
open class Adb(
    private val binary: String = "adb",
    private val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_S),
) {

    data class Output(val exitCode: Int, val stdout: String, val stderr: String)

    open fun run(serial: String?, vararg args: String): Output {
        val cmd = ArrayList<String>()
        cmd.add(binary)
        if (serial != null) {
            cmd.add("-s")
            cmd.add(serial)
        }
        cmd.addAll(args)
        val process = ProcessBuilder(cmd).redirectErrorStream(false).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IOException("adb ${args.joinToString(" ")} timed out after $timeout" + (serial?.let { " on $it" } ?: ""))
        }
        return Output(process.exitValue(), stdout, stderr)
    }

    /** `adb -s <serial> shell <args...>`, stdout trimmed. */
    fun shell(serial: String?, vararg args: String): String = run(serial, "shell", *args).stdout.trim()

    /** `getprop <name>`, trimmed. */
    fun prop(serial: String?, name: String): String = shell(serial, "getprop", name)

    /** Serials of attached devices in state `device` (not `offline`/`unauthorized`). */
    fun attachedSerials(): List<String> =
        run(null, "devices").stdout.lines().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[1] == "device") parts[0] else null
        }

    private companion object {
        const val DEFAULT_TIMEOUT_S = 90L
    }
}
