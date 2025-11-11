package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore

interface AndroidServicesModule {
    val preferencesService: PreferencesService
    val store: KeyValueStore
    val ordinalStore: OrdinalStore
}
