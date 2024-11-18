package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val initModule = InitModuleImpl()
        val coreModule = createCoreModule(mockk(relaxed = true), initModule)
        val module = AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = coreModule
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
