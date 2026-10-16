package io.embrace.android.embracesdk.internal.instrumentation.fcm

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class PushNotificationInstrumentationProviderTest {

    private val provider = PushNotificationInstrumentationProvider()

    @After
    fun tearDown() {
        fcmDataSource = null
    }

    @Test
    fun `disabling data capture clears static reference`() {
        val source = checkNotNull(provider.register(FakeInstrumentationArgs())?.invoke())
        assertSame(source, fcmDataSource)
        source.onDataCaptureDisabled()
        assertNull(fcmDataSource)
    }

    @Test
    fun `disabling a stale data source does not clear a newer one`() {
        val factory = checkNotNull(provider.register(FakeInstrumentationArgs()))
        val stale = checkNotNull(factory())
        val current = checkNotNull(factory())
        stale.onDataCaptureDisabled()
        assertSame(current, fcmDataSource)
    }
}
