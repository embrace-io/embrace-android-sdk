package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.api.delegate.FlutterInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.FlutterSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class FlutterInternalInterfaceImplTest {

    private lateinit var impl: FlutterInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var logger: InternalLogger
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var store: FakeKeyValueStore

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        store = FakeKeyValueStore()
        hostedSdkVersionInfo = FlutterSdkVersionInfo(store)
        impl = FlutterInternalInterfaceImpl(embrace, mockk(), hostedSdkVersionInfo, logger)
    }

    @Test
    fun testSetFlutterSdkVersionNotStarted() {
        every { embrace.isStarted } returns false
        impl.setEmbraceFlutterSdkVersion("2.12")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
        }
    }

    @Test
    fun testSetDartVersionNotStarted() {
        every { embrace.isStarted } returns false
        impl.setDartVersion("2.12")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
        }
    }
}
