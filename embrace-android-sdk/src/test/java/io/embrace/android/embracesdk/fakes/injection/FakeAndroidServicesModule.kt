package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.prefs.PreferencesService

internal class FakeAndroidServicesModule(
    override val preferencesService: PreferencesService = FakePreferenceService()
) : AndroidServicesModule
