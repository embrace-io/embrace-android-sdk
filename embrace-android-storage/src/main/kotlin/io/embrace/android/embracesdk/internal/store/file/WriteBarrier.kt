package io.embrace.android.embracesdk.internal.store.file

/**
 * Holds a store's background writes until [open] is called, so they can't race another writer of the same file.
 */
class WriteBarrier(open: Boolean = false) {

    private val lock = Any()

    private var held: MutableList<() -> Unit>? = if (open) null else mutableListOf()

    val isOpen: Boolean
        get() = synchronized(lock) { held == null }

    /**
     * Runs [write] now if the barrier is open, or once it opens.
     */
    fun runWhenOpen(write: () -> Unit) {
        val queued = synchronized(lock) { held?.add(write) ?: false }
        if (!queued) {
            write()
        }
    }

    /**
     * Opens the barrier and runs the writes it held, in the order they were requested. One that throws doesn't stop
     * the rest, and the first failure is rethrown once they have all run.
     */
    fun open() {
        val writes = synchronized(lock) { held.also { held = null } } ?: return
        var failure: Throwable? = null
        for (write in writes) {
            try {
                write()
            } catch (exc: Throwable) {
                failure = failure ?: exc
            }
        }
        failure?.let { throw it }
    }
}
