package io.embrace.android.embracesdk.internal.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AndroidServicesModuleImplTest {

    @Test
    fun testDefault() {
        val initModule = InitModuleImpl()
        val coreModule = CoreModuleImpl(ApplicationProvider.getApplicationContext(), initModule)
        val module = AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = coreModule
        )

        assertTrue(module.preferencesService is EmbracePreferencesService)
    }
}
