package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase.ABANDONED
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase.ADOPTED
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase.COMMITTED
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase.COMMITTING
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase.DECODING
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.store.file.WriteBarrier

/**
 * Orders the prefs migration's writes against the install-state stores, which may give up waiting for it. The migrator
 * writes the install state only inside [commit], and [writes] holds the stores' own writes until then. [settle] and
 * [skip] never wait on the migrator's writes.
 */
class PrefsMigrationGate {

    val writes: WriteBarrier = WriteBarrier()

    private val lock = Any()
    private var phase = DECODING

    /**
     * The state the stores start from, or null if they read their files. Read it after [settle].
     */
    var migrated: MigratedInstallState? = null
        get() = synchronized(lock) { field }
        private set

    /**
     * The staged ordinals, already mapped, which the ordinal store uses instead of `ordinals.bin`.
     */
    var ordinals: MmapOrdinalStore? = null
        get() = synchronized(lock) { field }
        private set

    /**
     * Runs [write] unless the stores have already settled or the migration was skipped, and returns whether it ran.
     * [write] writes [decoded], which is null if there was nothing to migrate or the ordinals couldn't be staged,
     * commits [staged] if it can, and returns false if it started the install fresh instead.
     */
    fun commit(decoded: MigratedInstallState?, staged: MmapOrdinalStore?, write: () -> Boolean): Boolean {
        val begun = synchronized(lock) {
            (phase == DECODING).also {
                if (it) {
                    migrated = decoded
                    ordinals = staged
                    phase = COMMITTING
                }
            }
        }
        if (!begun) {
            return false
        }
        var written = false
        try {
            written = write()
        } finally {
            finish(written)
        }
        return true
    }

    /**
     * Marks the migration as done by a previous launch.
     */
    fun skip() {
        synchronized(lock) { phase = COMMITTED }
        writes.open()
    }

    /**
     * Abandons a migration that is still decoding, or adopts the decoded state, if any, of one that is writing. Returns
     * [ABANDONED], [ADOPTED] or [COMMITTED].
     */
    fun settle(): PrefsMigrationPhase {
        val settled = synchronized(lock) {
            phase = when (phase) {
                DECODING -> ABANDONED
                COMMITTING -> ADOPTED
                else -> phase
            }
            phase
        }
        if (settled == ABANDONED) {
            writes.open()
        }
        return settled
    }

    private fun finish(written: Boolean) {
        val open = synchronized(lock) {
            val adopted = phase == ADOPTED && ordinals != null
            if (!adopted && !written) {
                migrated = null
                ordinals = null
            }
            phase = COMMITTED
            written || !adopted
        }
        if (open) {
            writes.open()
        }
    }
}
