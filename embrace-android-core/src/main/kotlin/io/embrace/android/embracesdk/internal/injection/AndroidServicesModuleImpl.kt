@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.Worker

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
            workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
            lazyPrefs,
            initModule.clock,
            initModule.jsonSerializer
        )
    }
}
