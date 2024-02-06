@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface AndroidServicesModule {
    val preferencesService: PreferencesService
}

internal class AndroidServicesModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) : AndroidServicesModule {

    override val preferencesService: PreferencesService by singleton {
        val lazyPrefs = lazy {
            PreferenceManager.getDefaultSharedPreferences(
                coreModule.context
            )
        }
        EmbracePreferencesService(
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
            lazyPrefs,
            initModule.clock,
            coreModule.jsonSerializer
        )
    }
}
