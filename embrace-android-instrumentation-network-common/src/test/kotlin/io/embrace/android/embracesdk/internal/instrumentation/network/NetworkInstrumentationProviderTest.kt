package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class NetworkInstrumentationProviderTest {

    @After
    fun tearDown() {
        networkCaptureDataSource = null
        networkRequestDataSource = null
    }

    @Test
    fun `disabling network capture clears static reference`() {
        val source = checkNotNull(NetworkCaptureInstrumentationProvider().register(FakeInstrumentationArgs())?.invoke())
        assertSame(source, retrieveNetworkCaptureDataSource())
        source.onDataCaptureDisabled()
        assertNull(retrieveNetworkCaptureDataSource())
    }

    @Test
    fun `disabling network request clears static reference`() {
        val source = checkNotNull(NetworkRequestInstrumentationProvider().register(FakeInstrumentationArgs())?.invoke())
        assertSame(source, retrieveNetworkRequestDataSource())
        source.onDataCaptureDisabled()
        assertNull(retrieveNetworkRequestDataSource())
    }

    @Test
    fun `disabling a stale data source does not clear a newer one`() {
        val factory = checkNotNull(NetworkCaptureInstrumentationProvider().register(FakeInstrumentationArgs()))
        val stale = checkNotNull(factory())
        val current = checkNotNull(factory())
        stale.onDataCaptureDisabled()
        assertSame(current, retrieveNetworkCaptureDataSource())
    }
}
