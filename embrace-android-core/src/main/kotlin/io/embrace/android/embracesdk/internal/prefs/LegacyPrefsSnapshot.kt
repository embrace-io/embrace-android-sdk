package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState

/**
 * Every live value the SDK kept in [android.content.SharedPreferences], grouped by the file it migrates to.
 */
data class LegacyPrefsSnapshot(

    /**
     * Destined for `persisted_sdk_state.bin`.
     */
    val persistedSdkState: PersistedSdkState,

    /**
     * Destined for `ordinals.bin`. Ordinals that were never stored are absent.
     */
    val ordinals: Map<Ordinal, Int>,

    /**
     * The raw scope of [Ordinal.APP_VERSION_STARTUP], destined for `ordinals.bin`.
     */
    val appVersionStartupScope: String?,

    /**
     * Remaining capture counts keyed by network capture rule ID, destined for `network_capture.bin`.
     */
    val networkCaptureRemaining: Map<String, Int>,

    /**
     * Destined for `aei_delivered.bin`.
     */
    val deliveredAeiIds: Set<String>,
)
