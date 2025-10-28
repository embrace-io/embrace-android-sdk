package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.store.KeyValueStore

interface AndroidServicesModule {
    val preferencesService: PreferencesService
    val store: KeyValueStore
}
