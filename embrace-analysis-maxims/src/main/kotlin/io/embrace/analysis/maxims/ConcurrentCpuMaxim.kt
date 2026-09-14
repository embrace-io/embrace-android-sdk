package io.embrace.analysis.maxims

/** concurrent-cpu: competing processes' on-CPU share of the window makes a slow init likelier. */
internal object ConcurrentCpuMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "concurrent-cpu",
        scope = Maxims.DIRECTIONAL,
        statement = "Concurrent CPU from other processes makes a slow init likelier, by 1.5x or more where measurable: " +
            "the highest quartile of other-process on-CPU share of the window, the idle task excluded.",
        why = "The bench's extreme-outlier class: system_server GC compactions, dexopt and app-ecosystem churn inflate " +
            "the window through the memory bus while the main thread's own wait stays small. Proven causal by " +
            "inducing churn; how much of it a device sees is the device's.",
        check = Lifts.liftCheck({ it.competitorShare }, lowest = false, floor = 1.5, directional = true),
    )
}
