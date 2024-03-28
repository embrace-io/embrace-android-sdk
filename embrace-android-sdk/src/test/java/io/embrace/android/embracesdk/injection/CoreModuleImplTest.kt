package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class CoreModuleImplTest {

    @Test
    fun testApplicationObject() {
        val ctx = RuntimeEnvironment.getApplication().applicationContext
        val logger = InternalEmbraceLogger()
        val module = CoreModuleImpl(ctx, Embrace.AppFramework.NATIVE, logger)
        assertSame(ctx, module.context)
        assertSame(ctx, module.application)
        assertNotNull(module.serviceRegistry)
        assertNotNull(module.jsonSerializer)
    }

    @Test
    fun testContextObject() {
        val application = RuntimeEnvironment.getApplication()
        val isDebug = AppEnvironment(application.applicationInfo).isDebug
        val ctx = application.applicationContext
        val module = CoreModuleImpl(ctx, Embrace.AppFramework.NATIVE, InternalEmbraceLogger())
        assertSame(application, module.application)
        assertEquals(isDebug, module.isDebug)
    }
}
