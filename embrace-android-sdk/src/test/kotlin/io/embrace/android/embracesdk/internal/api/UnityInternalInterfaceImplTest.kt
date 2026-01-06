package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.api.delegate.UnityInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class UnityInternalInterfaceImplTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var impl: UnityInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var store: FakeKeyValueStore
    private lateinit var logger: InternalLogger

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        store = FakeKeyValueStore()
        logger = mockk(relaxed = true)
        hostedSdkVersionInfo = UnitySdkVersionInfo(store)
        impl = UnityInternalInterfaceImpl(embrace, mockk(), hostedSdkVersionInfo, logger)
    }

    @Test
    fun testSetUnityMetaDataNotStarted() {
        every { embrace.isStarted } returns false
        impl.setUnityMetaData("unityVersion", "buildGuid", "unitySdkVersion")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
        }
    }

    @Test
    fun testSetUnityMetaDataNull() {
        every { embrace.isStarted } returns true
        impl.setUnityMetaData(null, null, "unitySdkVersion")
        assertEquals(emptyMap<String, Any?>(), store.values())
    }
}
