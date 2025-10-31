@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore

internal class AndroidServicesModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
) : AndroidServicesModule {

    override val store: KeyValueStore by singleton {
        SharedPrefsStore(
            PreferenceManager.getDefaultSharedPreferences(
                coreModule.context
            ),
            initModule.jsonSerializer
        )
    }

    override val preferencesService: PreferencesService by singleton {
        EmbracePreferencesService(
            store,
            initModule.clock
        )
    }
}
