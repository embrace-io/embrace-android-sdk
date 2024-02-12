package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val initModule = InitModuleImpl()
        val module = AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = FakeCoreModule(),
            workerThreadModule = WorkerThreadModuleImpl(initModule)
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
