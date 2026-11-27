package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.instrumentation.leaks.LeakDetector.LeakSnapshot

/**
 * Encodes [suspects] as version 2 of `emb.memory_leak_suspects`:
 * `2;<sessionPartId>_<userSessionId>:<objectType>|<className>|<cycles>,...;...` - groups separated by `;`, rows by `,` and
 * fields by `|`, none of which can appear in a JVM class name, so no escaping is needed. Each group is the session part
 * its suspects were tracked closed in, and has one row per object type and class, whose `<cycles>` sums the GC cycles its
 * suspects survived since the previous report - so summing a class's rows over session parts never double counts.
 *
 * Suspects whose token isn't a [LeakContext], or whose session IDs are empty, are left out: there is nothing to associate
 * them with. Session part attributes are not length-truncated by the platform, so [maxLength] is enforced here -
 * defaulting to 2000, matching `OtelLimitsConfig.getMaxInternalAttributeValueLength()`. Rows are tried most cycles first;
 * one that doesn't fit is left out, and its cycles go unreported, while later, shorter rows are still tried.
 */
internal fun encodeLeakSuspects(suspects: List<LeakSnapshot>, maxLength: Int = MAX_ENCODED_LENGTH): String {
    val rows = GroupedRows(maxLength - SUSPECTS_VERSION.length)
    groupByRow(suspects)
        .map { (key, snapshots) -> SummedRow(key, snapshots.sumOf { it.cyclesSurvived }) }
        .sortedWith { a, b -> b.cycles.compareTo(a.cycles) }
        .forEach { row ->
            val text = "${row.key.objectType}|${row.key.className}|${row.cycles}"
            if (text.length <= rows.budgetFor(row.key.origin)) {
                rows.add(row.key.origin, text)
            }
        }
    return if (rows.isEmpty()) "" else rows.renderAfter(SUSPECTS_VERSION)
}

/**
 * Encodes the close times of the [suspects] confirmed since the previous report ([LeakSnapshot.isNew]) as
 * `emb.memory_leak_candidates`:
 * `<baseMs>;<dropped>;<sessionPartId>_<userSessionId>:<objectType>|<className>|<closedAgoMs>.<closedAgoMs>,...;...`.
 * Each `<closedAgoMs>` is [baseMs] minus a suspect's close time, which ties that one instance back to its leak point when
 * debugging. Each suspect is new in exactly one report, so its close time is sent once.
 *
 * Suspects are left out as for [encodeLeakSuspects], and [maxLength] is enforced the same way: rows with the most
 * candidates are tried first, close times oldest first, and any that don't fit are left out. Nothing is encoded when
 * there are no close times to write and nothing was [dropped].
 */
internal fun encodeLeakCandidates(
    suspects: List<LeakSnapshot>,
    baseMs: Long,
    dropped: Int,
    maxLength: Int = MAX_ENCODED_LENGTH,
): String {
    val header = "$baseMs;$dropped"
    val rows = GroupedRows(maxLength - header.length)
    groupByRow(suspects.filter { it.isNew }).entries
        .sortedWith { a, b -> b.value.size.compareTo(a.value.size) }
        .forEach { (key, snapshots) ->
            closeTimesRow(key, snapshots, baseMs, rows.budgetFor(key.origin))?.let { rows.add(key.origin, it) }
        }
    return if (rows.isEmpty() && dropped == 0) "" else rows.renderAfter(header)
}

/**
 * Renders [key]'s row with as many of [snapshots]' close times as fit in [budget], oldest first, or null if none fit.
 */
private fun closeTimesRow(key: RowKey, snapshots: List<LeakSnapshot>, baseMs: Long, budget: Int): String? {
    val row = StringBuilder().append(key.objectType).append('|').append(key.className).append('|')
    val prefixLength = row.length
    for (snapshot in snapshots.sortedWith { a, b -> a.trackedAtMs.compareTo(b.trackedAtMs) }) {
        val closedAgo = (baseMs - snapshot.trackedAtMs).toString()
        val separator = if (row.length > prefixLength) 1 else 0
        if (row.length + separator + closedAgo.length > budget) {
            break
        }
        if (separator > 0) {
            row.append('.')
        }
        row.append(closedAgo)
    }
    return if (row.length > prefixLength) row.toString() else null
}

/**
 * Groups [suspects] into rows by the session part they were tracked closed in, their object type and their class,
 * leaving out those with nothing to associate them with.
 */
private fun groupByRow(suspects: List<LeakSnapshot>): Map<RowKey, List<LeakSnapshot>> {
    val rows = LinkedHashMap<RowKey, MutableList<LeakSnapshot>>()
    for (snapshot in suspects) {
        val context = snapshot.token as? LeakContext ?: continue
        val ids = context.sessionIds
        if (ids.sessionPartId.isNotEmpty() && ids.userSessionId.isNotEmpty()) {
            val origin = "${ids.sessionPartId}_${ids.userSessionId}"
            rows.getOrPut(RowKey(origin, context.objectType, context.reportedClass.name)) { ArrayList() }.add(snapshot)
        }
    }
    return rows
}

private data class RowKey(val origin: String, val objectType: String, val className: String)

private class SummedRow(val key: RowKey, val cycles: Long)

/**
 * Rows grouped by origin within a total length budget: a group costs `;<origin>:` the first time it is used, and each row
 * after the first in a group costs a `,`.
 */
private class GroupedRows(private var remaining: Int) {
    private val groups = LinkedHashMap<String, MutableList<String>>()

    /**
     * How long a row added to [origin]'s group may be.
     */
    fun budgetFor(origin: String): Int = remaining - if (origin in groups) 1 else origin.length + 2

    fun add(origin: String, row: String) {
        remaining = budgetFor(origin) - row.length
        groups.getOrPut(origin) { ArrayList() }.add(row)
    }

    fun isEmpty(): Boolean = groups.isEmpty()

    fun renderAfter(header: String): String = buildString {
        append(header)
        for ((origin, rows) in groups) {
            append(';').append(origin).append(':')
            rows.joinTo(this, ",")
        }
    }
}

private const val SUSPECTS_VERSION = "2"
private const val MAX_ENCODED_LENGTH = 2000
