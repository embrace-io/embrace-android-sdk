package io.embrace.android.embracesdk.internal.injection

import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.worker.WorkerName
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModule

internal class AndroidServicesModuleImpl(
    initModule: io.embrace.android.embracesdk.internal.injection.InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) : io.embrace.android.embracesdk.internal.injection.AndroidServicesModule {

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
            initModule.jsonSerializer
        )
    }
}
