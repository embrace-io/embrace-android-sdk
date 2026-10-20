package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.ContextWrapper
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

internal class ApplicationContextTest {

    @Test
    fun testApplicationObject() {
        val ctx = Application()
        assertSame(ctx, ctx.applicationContextOrSelf())
    }

    @Test
    fun testContextObject() {
        val application = Application()
        val ctx = object : ContextWrapper(null) {
            override fun getApplicationContext() = application
        }
        assertSame(application, ctx.applicationContextOrSelf())
    }

    @Test
    fun `test environment`() {
        assertEquals(Environment.DEV, AppEnvironment(true).environment)
        assertEquals(Environment.PROD, AppEnvironment(false).environment)
    }
}
