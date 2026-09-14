package io.embrace.analysis.campaign

/**
 * One precondition of a matrix cell, machine-checked by [CellRunner] before any pass runs. Every input
 * is bound at construction, so the runner holds a plain list and checks it in order. A failed check is a
 * STOP, never a warning: each of these has already cost a wasted or wrong campaign.
 */
fun interface Invariant {
    fun check(): Check
}

/** An invariant's verdict: the name the provenance records it under, whether it held, and the wording. */
data class Check(val name: String, val ok: Boolean, val detail: String)
