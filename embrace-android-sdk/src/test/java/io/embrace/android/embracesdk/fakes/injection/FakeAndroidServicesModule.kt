package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class FakeAndroidServicesModule(
    override val preferencesService: PreferencesService = FakePreferenceService()
) : io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
