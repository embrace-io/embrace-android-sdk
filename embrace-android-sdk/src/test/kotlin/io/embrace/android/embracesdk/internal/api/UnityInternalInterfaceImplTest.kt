package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.UnitySdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class UnityInternalInterfaceImplTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var impl: HostedSdkInternalInterfaceImpl
    private lateinit var store: FakeKeyValueStore
    private lateinit var logger: InternalLogger

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        logger = mockk(relaxed = true)
        hostedSdkVersionInfo = UnitySdkVersionInfo(store)
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = mockk(),
            configService = mockk(),
            resourceSource = mockk(),
            instrumentationRegistry = mockk(),
            serializer = mockk(),
            rnBundleIdTracker = lazy { mockk() },
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            logger = logger,
            telemetryService = FakeInternalTelemetryService(),
        )
    }

    @Test
    fun testSetUnityMetaDataNull() {
        impl.setUnityMetaData(null, null, "unitySdkVersion")
        assertEquals(emptyMap<String, Any?>(), store.values())
    }

    @Test
    fun testSetUnityMetaDataUsesSingleCommit() {
        impl.setUnityMetaData("unityVersion", "buildGuid", "unitySdkVersion")

        assertEquals(1, store.commitCount)
        assertEquals("unityVersion", hostedSdkVersionInfo.hostedPlatformVersion)
        assertEquals("unitySdkVersion", hostedSdkVersionInfo.hostedSdkVersion)
        assertEquals("buildGuid", hostedSdkVersionInfo.unityBuildIdNumber)
    }
}
