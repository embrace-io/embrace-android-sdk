package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.PersistedSdkState

/**
 * The install state decoded from the prefs, other than the ordinals, as the migration writes it to the install-state stores' files.
 */
data class MigratedInstallState(
    val persistedSdkState: PersistedSdkState,
    val networkCaptureUsed: Map<String, Int>,
    val deliveredAeiIds: Set<String>,
)
