package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.prefs.EmbracePreferencesService
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val module = AndroidServicesModuleImpl(
            initModule = InitModuleImpl(),
            coreModule = FakeCoreModule(),
            workerThreadModule = FakeWorkerThreadModule()
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
