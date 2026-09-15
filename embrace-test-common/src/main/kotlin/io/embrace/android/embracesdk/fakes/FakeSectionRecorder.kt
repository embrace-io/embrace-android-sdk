package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.SectionRecorder

class FakeSectionRecorder : SectionRecorder {

    val sections: MutableList<String> = mutableListOf()
    var endedSections: Int = 0
        private set

    val counters: MutableMap<String, MutableList<Long>> = LinkedHashMap()

    override fun beginSection(sectionName: String): Boolean {
        sections.add(sectionName)
        return true
    }

    override fun endSection() {
        endedSections++
    }

    override fun setCounter(name: String, value: Long) {
        counters.getOrPut(name) { mutableListOf() }.add(value)
    }

    fun latestCounter(name: String): Long? = counters[name]?.lastOrNull()
}
