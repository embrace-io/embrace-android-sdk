package io.embrace.analysis.maxims

/** scheduler-wait: any scheduler wait makes a slow init likelier, by the highest quartile of runnable-wait share. */
internal object SchedulerWaitMaxim {
    val MAXIM: Maxims.Maxim = Maxims.Maxim(
        id = "scheduler-wait",
        scope = Maxims.DIRECTIONAL,
        statement = "Any scheduler wait makes a slow init likelier, by 2x where the effect is measurable: the highest " +
            "quartile of runnable-wait share.",
        why = "A runnable thread that is not scheduled loses wall time the SDK cannot recover; how often that happens " +
            "is the device's. The bench proved the concurrent-CPU mechanism causal by inducing churn.",
        check = Lifts.liftCheck({ it.rqShare }, lowest = false, floor = 2.0, directional = true),
    )
}
