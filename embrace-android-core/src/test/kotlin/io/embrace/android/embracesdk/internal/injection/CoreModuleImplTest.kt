package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class CoreModuleImplTest : RobolectricTest() {

    private val initModule = FakeInitModule()

    @Test
    fun testApplicationObject() {
        val ctx = RuntimeEnvironment.getApplication().applicationContext
        val module = CoreModuleImpl(ctx, initModule)
        assertSame(ctx, module.context)
        assertSame(ctx, module.application)
    }

    @Test
    fun testContextObject() {
        val application = RuntimeEnvironment.getApplication()
        val ctx = application.applicationContext
        val module = CoreModuleImpl(ctx, initModule)
        assertSame(application, module.application)
    }

    @Test
    fun `test environment`() {
        assertEquals(Environment.DEV, AppEnvironment(true).environment)
        assertEquals(Environment.PROD, AppEnvironment(false).environment)
    }
}
