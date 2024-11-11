@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService

internal class AndroidServicesModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
) : AndroidServicesModule {

    override val preferencesService: PreferencesService by singleton {
        EmbracePreferencesService(
            PreferenceManager.getDefaultSharedPreferences(
                coreModule.context
            ),
            initModule.clock,
            initModule.jsonSerializer
        )
    }
}
