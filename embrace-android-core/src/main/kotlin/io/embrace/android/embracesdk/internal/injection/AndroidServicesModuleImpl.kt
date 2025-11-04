@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl

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

    override val ordinalStore: OrdinalStore by singleton {
        OrdinalStoreImpl(store)
    }

    override val preferencesService: PreferencesService by singleton {
        EmbracePreferencesService(
            store,
            initModule.clock
        )
    }
}
