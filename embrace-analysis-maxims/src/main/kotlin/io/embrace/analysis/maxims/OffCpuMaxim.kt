package io.embrace.analysis.maxims

/** off-cpu: a slow init is one whose main thread was off-CPU, by the lowest quartile of on-CPU share. */
internal object OffCpuMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "off-cpu",
        scope = Maxims.UNIVERSAL,
        statement = "A slow init is one whose main thread was off-CPU: the lowest quartile of on-CPU share is at least " +
            "1.5x as likely to be slow.",
        why = "Init is main-thread work; when the thread is not running, something else is holding it. Same claim as " +
            "the production maxim, read from the trace's thread states instead of init-cpu-pct.",
        check = Lifts.liftCheck({ it.cpuShare }, lowest = true, floor = 1.5, directional = false),
    )
}
