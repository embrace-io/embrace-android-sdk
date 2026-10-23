package io.embrace.android.embracesdk.internal.utils

internal class FakeTraceApi(var enabled: Boolean = true) : TraceApi {

    private val openSections = ArrayDeque<String>()

    /**
     * Sections that have been closed, in the order they were closed.
     */
    val previousSections: MutableList<String> = mutableListOf()

    val counters: MutableList<Pair<String, Long>> = mutableListOf()

    override fun isEnabled(): Boolean = enabled

    override fun beginSection(sectionName: String) {
        openSections.addLast(sectionName)
    }

    override fun endSection() {
        previousSections.add(openSections.removeLast())
    }

    override fun setCounter(counterName: String, counterValue: Long) {
        counters.add(counterName to counterValue)
    }
}
