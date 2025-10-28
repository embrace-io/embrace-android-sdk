package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.store.KeyValueStore
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

interface AndroidServicesModule {
    val preferencesService: PreferencesService
    val store: KeyValueStore
}
