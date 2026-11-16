package io.embrace.android.embracesdk.internal.prefs

/**
 * Potential states for the prefs migration is.
 */
enum class PrefsMigrationPhase {
    DECODING,
    COMMITTING,
    ADOPTED,
    COMMITTED,
    ABANDONED,
}
