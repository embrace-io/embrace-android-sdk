package io.embrace.android.embracesdk.internal.utils.concurrent

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around `AtomicBoolean` to clear up "exactly once" use-cases. A `OneShot` can only be claimed exactly once, by a single
 * thread.
 */
@JvmInline
value class OneShot private constructor(private val state: AtomicBoolean) {
    constructor() : this(AtomicBoolean(false))

    val isAvailable: Boolean get() = !isClaimed
    val isClaimed: Boolean get() = state.get()

    /**
     * Try claim this `OneShot` and return `true` if successful.
     */
    fun tryClaim(): Boolean = state.compareAndSet(false, true)

    /**
     * [tryClaim] this `OneShot` and run [block] only if successful.
     */
    inline operator fun invoke(block: () -> Unit) {
        if (tryClaim()) {
            block()
        }
    }
}
