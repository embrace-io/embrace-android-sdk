package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.ContextWrapper
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

internal class CoreModuleImplTest {

    private val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)

    @Test
    fun testApplicationObject() {
        val ctx = Application()
        val module = CoreModuleImpl(ctx, initModule)
        assertSame(ctx, module.context)
        assertSame(ctx, module.application)
    }

    @Test
    fun testContextObject() {
        val application = Application()
        val ctx = object : ContextWrapper(null) {
            override fun getApplicationContext() = application
        }
        val module = CoreModuleImpl(ctx, initModule)
        assertSame(application, module.application)
    }

    @Test
    fun `test environment`() {
        assertEquals(Environment.DEV, AppEnvironment(true).environment)
        assertEquals(Environment.PROD, AppEnvironment(false).environment)
    }
}
