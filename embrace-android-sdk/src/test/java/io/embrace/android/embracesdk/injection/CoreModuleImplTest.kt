package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
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
        val module = CoreModuleImpl(ctx, Embrace.AppFramework.NATIVE)
        assertSame(ctx, module.context)
        assertSame(ctx, module.application)
        assertSame(InternalStaticEmbraceLogger.logger, module.logger)
        assertNotNull(module.serviceRegistry)
        assertNotNull(module.jsonSerializer)
    }

    @Test
    fun testContextObject() {
        val application = RuntimeEnvironment.getApplication()
        val isDebug = application.applicationInfo.isDebug()
        val ctx = application.applicationContext
        val module = CoreModuleImpl(ctx, Embrace.AppFramework.NATIVE)
        assertSame(application, module.application)
        assertEquals(isDebug, module.isDebug)
    }
}
