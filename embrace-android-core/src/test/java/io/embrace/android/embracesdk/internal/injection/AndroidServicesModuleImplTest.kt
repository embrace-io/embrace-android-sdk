package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val initModule = InitModuleImpl()
        val coreModule = createCoreModule(mockk(relaxed = true))
        val module = AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = WorkerThreadModuleImpl()
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
