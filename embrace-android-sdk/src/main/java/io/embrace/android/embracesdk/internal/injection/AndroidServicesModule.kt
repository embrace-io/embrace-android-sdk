@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal interface AndroidServicesModule {
    val preferencesService: PreferencesService
}
