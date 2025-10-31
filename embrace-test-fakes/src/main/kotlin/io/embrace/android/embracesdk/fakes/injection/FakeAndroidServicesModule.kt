package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.store.KeyValueStore

class FakeAndroidServicesModule(
    override val preferencesService: PreferencesService = FakePreferenceService(),
    override val store: KeyValueStore = FakeKeyValueStore()
) : AndroidServicesModule
