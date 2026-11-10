package io.embrace.analysis.startup

/**
 * A trace's SDK-init window: how long it lasted, the slice it was read from, and what the thread that runs SDK init
 * did inside it.
 */
data class StartupWindow(
    val durationMs: Double,
    val source: Source,
    val initThread: ThreadScheduling,
) {

    /**
     * The slice the window was read from, by the [key] `startup_views.sql` gives it, and how the report
     * [describes][description] it.
     */
    enum class Source(val key: String, val description: String) {
        SDK_START(
            key = "emb-sdk-start",
            description = "emb-sdk-start slice (wraps Embrace.start(); ~<1 ms wider than the exported emb-embrace-init span)",
        ),

        /**
         * Older SDKs (.claude/skills/startup-analysis/references/section-history.md): a trace without emb-sdk-start has
         * a window composed from the first modules-init start to the first post-services-setup end.
         */
        COMPOSED(
            key = "composed",
            description = "composed: modules-init start -> post-services-setup end (= exported emb-embrace-init span; " +
                "emb-sdk-start absent in this SDK version)",
        ),
    }

    /**
     * The init thread's scheduling inside the window: its running time, its runnable wait (the time it was ready to run
     * but not running, the scheduler-contention signal), and the distinct CPUs it ran on.
     */
    data class ThreadScheduling(
        val runningMs: Double,
        val waitMs: Double,
        val cpus: Int,
    )
}
