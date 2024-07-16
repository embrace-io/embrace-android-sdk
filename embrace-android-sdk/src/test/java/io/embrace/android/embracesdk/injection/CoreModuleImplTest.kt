package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.injection.CoreModuleImpl
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
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
        val logger = EmbLoggerImpl()
        val module = CoreModuleImpl(ctx, logger)
        assertSame(ctx, module.context)
        assertSame(ctx, module.application)
        assertNotNull(module.serviceRegistry)
    }

    @Test
    fun testContextObject() {
        val application = RuntimeEnvironment.getApplication()
        val isDebug = AppEnvironment(application.applicationInfo).isDebug
        val ctx = application.applicationContext
        val module = CoreModuleImpl(ctx, EmbLoggerImpl())
        assertSame(application, module.application)
        assertEquals(isDebug, module.isDebug)
    }
}
