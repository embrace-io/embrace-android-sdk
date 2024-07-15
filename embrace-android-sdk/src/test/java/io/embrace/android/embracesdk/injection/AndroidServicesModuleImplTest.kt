package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val initModule = InitModuleImpl()
        val coreModule = FakeCoreModule()
        val module = io.embrace.android.embracesdk.internal.injection.AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = WorkerThreadModuleImpl(initModule)
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
