package io.embrace.startup.campaign

import java.nio.file.Files
import java.nio.file.Path

/**
 * Borrow a piece of shared mutable state and give it back even if the process is killed.
 *
 * Campaigns mutate things that outlive them - above all the ExampleApp gradle catalog's SDK version
 * pin, which a campaign flips per version leg. If the process dies holding it, the next unrelated
 * build silently resolves the wrong SDK and nothing about the failure points at the cause.
 *
 * Three layers, because each covers what the others cannot:
 * 1. a MARKER FILE - survives SIGKILL, power loss, the machine going down; the only durable layer;
 * 2. a JVM shutdown hook - runs on SIGTERM/SIGINT/normal exit (not SIGKILL), matching what a signal handler would catch;
 * 3. the `finally` of [use] - normal completion and exceptions.
 *
 * Ordering rule that matters more than it looks: [recover] runs BEFORE the current value is read.
 * Otherwise a stale value left by a dead run is mistaken for the user's own setting and faithfully
 * "restored" on exit, which makes the damage permanent instead of repairing it.
 */
class BorrowedState(
    private val marker: Path,
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val log: (String) -> Unit = { println(it) },
) {
    var original: String? = null
        private set
    private var restored = false
    private var hook: Thread? = null

    /** Restore a value left behind by a run that died. Call BEFORE reading the current one. */
    fun recover() {
        if (!Files.exists(marker)) return
        val stale = Files.readString(marker).trim()
        Files.deleteIfExists(marker)
        if (stale.isNotEmpty() && read() != stale) {
            log("RECOVERY: previous run left state unrestored; setting back to $stale")
            write(stale)
        }
    }

    /** Idempotent: safe from the shutdown hook and the finally block both. */
    @Synchronized
    fun restore(reason: String) {
        val value = original
        if (restored || value == null) return
        restored = true
        write(value)
        log("restored borrowed state ($reason): ${read()}")
        Files.deleteIfExists(marker)
    }

    /** Borrow for the duration of [block]; the block receives the original value. */
    fun <T> use(block: (original: String) -> T): T {
        recover()
        val current = read()
        original = current
        marker.parent?.let { Files.createDirectories(it) }
        Files.writeString(marker, current)
        val shutdown = Thread { restore("shutdown signal") }
        hook = shutdown
        runCatching { Runtime.getRuntime().addShutdownHook(shutdown) }
            .onFailure { log("note: could not install a shutdown hook (${it.message})") }
        try {
            return block(current)
        } catch (e: Exception) {
            restore("exception ${e::class.simpleName}")
            throw e
        } finally {
            restore("normal exit")
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
        }
    }
}
