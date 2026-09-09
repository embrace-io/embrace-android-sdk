package io.embrace.android.embracesdk.internal.utils.event

fun interface EventHandler<E> {
    fun onEvent(event: E)
}
