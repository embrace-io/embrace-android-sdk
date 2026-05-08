package io.embrace.android.exampleapp.paradigms

import android.content.Context

class ParadigmRun(
    val paradigm: NavParadigm,
    val style: NavStyle,
    val launch: (Context) -> Unit,
) {
    val key: String = "${paradigm.name.lowercase()}_${style.name.lowercase()}"
}
