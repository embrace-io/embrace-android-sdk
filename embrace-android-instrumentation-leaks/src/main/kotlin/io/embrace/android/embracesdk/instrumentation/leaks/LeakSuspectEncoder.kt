package io.embrace.android.embracesdk.instrumentation.leaks

/**
 * Encodes [suspects] into a single delimited string for reporting as one session attribute, grouped by the session part
 * each suspect was originally tracked closed in.
 *
 * Every currently-tracked suspect is included, regardless of which session part it originally belongs to: a suspect that
 * survives across a session part boundary is meant to be reported again in each part it survives, with a larger
 * [LeakDetector.LeakSnapshot.cyclesSurvived] each time - a time series of the same leak, rather than one report that has
 * to guess which part "owns" it. Most of the time every suspect shares the same session part - the one about to end -
 * but once something survives a boundary the live set can be a genuine mix of origins, which is why grouping is needed
 * rather than assumed away.
 *
 * A suspect whose [LeakDetector.LeakSnapshot.token] is not a [LeakContext], or whose session IDs are empty, is dropped: a
 * leak whose session is unknown is not reportable.
 *
 * The format is `<sessionPartId>_<userSessionId>:<objectType>|<className>|<cyclesSurvived>|<id>,...;...` - groups
 * separated by `;`, entries within a group by `,`, fields within an entry by `|`. None of these, nor `:`, can appear in a
 * JVM class name, so no escaping is needed. `<id>` is [LeakDetector.LeakSnapshot.id] rendered as hex, matching the
 * unsigned convention `Object.toString()` already uses for identity hashcodes.
 *
 * Session-part attributes are not length-truncated by the platform the way custom attributes are, so [maxLength] is
 * enforced here - defaulting to 2000, matching `OtelLimitsConfig.getMaxInternalAttributeValueLength()`. Suspects are kept
 * by [LeakDetector.LeakSnapshot.cyclesSurvived] descending, since the most persistent suspects are the most actionable,
 * and the actual rendered length is checked after each tentative addition - rather than approximated from an assumed
 * entry size - so the result never exceeds [maxLength] regardless of how long class names get. The first candidate that
 * would not fit stops the process; a later, shorter candidate is not tried in its place.
 */
internal fun encodeLeakSuspects(
    suspects: List<LeakDetector.LeakSnapshot>,
    maxLength: Int = MAX_ENCODED_LENGTH,
): String {
    val candidates = suspects.mapNotNull(::toCandidate).sortedByDescending { it.cyclesSurvived }

    val included = LinkedHashMap<String, List<String>>()
    for (candidate in candidates) {
        val attempt = LinkedHashMap(included)
        attempt[candidate.groupKey] = (attempt[candidate.groupKey] ?: emptyList()) + candidate.entry

        if (render(attempt).length > maxLength) {
            break
        }

        included[candidate.groupKey] = attempt.getValue(candidate.groupKey)
    }

    return render(included)
}

private fun toCandidate(snapshot: LeakDetector.LeakSnapshot): Candidate? {
    val context = snapshot.token as? LeakContext ?: return null
    val sessionIds = context.sessionIds
    if (sessionIds.userSessionId.isEmpty() || sessionIds.sessionPartId.isEmpty()) {
        return null
    }

    return Candidate(
        groupKey = "${sessionIds.sessionPartId}_${sessionIds.userSessionId}",
        entry = "${context.objectType}|${snapshot.className}|${snapshot.cyclesSurvived}|${Integer.toHexString(snapshot.id)}",
        cyclesSurvived = snapshot.cyclesSurvived,
    )
}

private fun render(groups: Map<String, List<String>>): String =
    groups.entries.joinToString(";") { (groupKey, entries) -> "$groupKey:${entries.joinToString(",")}" }

/**
 * One suspect that survived the [toCandidate] filter. [cyclesSurvived] is carried separately from the already-built
 * [entry] string so that [encodeLeakSuspects] can sort by it without re-parsing that string.
 */
private class Candidate(
    val groupKey: String,
    val entry: String,
    val cyclesSurvived: Long,
)

private const val MAX_ENCODED_LENGTH = 2000
