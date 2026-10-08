package io.embrace.android.embracesdk.internal.utils.event

/**
 * A typed request carried by a [BusPoll], and read by the [PollHandler]s that understand it. A poll can carry any number of queries,
 * and each handler finds the one it understands by type with [BusPoll.query], ignoring the rest.
 *
 * Queries come from two places. A caller can write its own (such as the granularity it wants a value reported at), in which case the
 * query type is part of the contract between the caller and the handlers, and needs to be declared where both can see it (as the
 * [PollKey] is). A handler can also return a query as [PollResult.next], to be carried in the following poll. These hold the handler's
 * own state, so their types should be private to the handler's module: [BusPoll.query] finds the first query of a type, so a `next`
 * query of a shared type would be read by every handler that understands that type.
 *
 * The `next` query is what allows handlers to report sampled data without holding onto the samples themselves. Many values are only
 * meaningful across an interval: CPU% is not a reading taken at a moment, it is "CPU% between A and B". Rather than the handler keeping
 * a buffer of samples (which is either large, or grows without bound when nothing polls it), it records just enough to measure from (such
 * as the cumulative CPU time and the timestamp at A) in its `next` query. When the following poll arrives carrying that query, the
 * handler measures the interval from A to now (B), and returns a new `next` query for B. A poll without one is the start of a new
 * interval, so the handler will typically return only a `next` query and no result, as there is nothing to measure yet.
 *
 * Because the caller holds the `next` query rather than the handler, one handler can serve any number of callers at different
 * granularities at the same time. A sampler polling at 4hz and another polling every 30 seconds each carry their own queries, and each
 * gets results covering exactly its own interval from the same handler, which keeps no per-caller state at all.
 *
 * A `next` query is a request for the following poll, not a signal to poll again immediately: every poll reaches every handler, so it
 * can't be used to page through the results of one. Callers should treat `next` queries as opaque, carrying `results.nextQueries()` from
 * one poll into the next alongside any queries of their own.
 */
interface PollQuery

/**
 * A poll to retrieve data from all the known [PollHandler]s via the given `PollKey`. Polls are a request for state that can be pulled
 * from other modules. Polls can be thought of as event broadcasts with results, where the data flow is primarily from the handler to the
 * emitter (rather than from the emitter to the handler).
 */
class BusPoll<V : Any>(
    val key: PollKey<V>,
    val queries: List<PollQuery> = emptyList(),
) {
    /**
     * Returns the [PollQuery] of type [Q] this poll carries, or `null` if it carries none.
     */
    inline fun <reified Q : PollQuery> query(): Q? {
        return queries.firstOrNull { it is Q } as Q?
    }
}

/**
 * Poll request may return any combination of a result value and a [PollQuery] to be carried in the next poll.
 */
class PollResult<V : Any>(
    val result: V?,
    val next: PollQuery? = null,
)

fun <V : Any> List<PollResult<V>>.results(): List<V> {
    return mapNotNull { it.result }
}

fun List<PollResult<*>>.nextQueries(): List<PollQuery> {
    return mapNotNull { it.next }
}
