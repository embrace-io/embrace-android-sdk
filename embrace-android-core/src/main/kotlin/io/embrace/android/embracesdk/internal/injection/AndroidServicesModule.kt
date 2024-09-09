package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

interface AndroidServicesModule {
    val preferencesService: PreferencesService
}
