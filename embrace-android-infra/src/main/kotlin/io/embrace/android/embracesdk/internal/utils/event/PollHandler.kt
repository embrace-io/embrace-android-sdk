package io.embrace.android.embracesdk.internal.utils.event

fun interface PollHandler<V : Any> {
    fun onPoll(poll: BusPoll<V>): PollResult<V>
}
