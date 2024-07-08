package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.api.delegate.UnityInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class UnityInternalInterfaceImplTest {

    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var impl: UnityInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var preferencesService: PreferencesService
    private lateinit var logger: EmbLogger

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        preferencesService = FakePreferenceService()
        logger = mockk(relaxed = true)
        hostedSdkVersionInfo = HostedSdkVersionInfo(
            preferencesService,
            AppFramework.UNITY
        )
        impl = UnityInternalInterfaceImpl(embrace, mockk(), hostedSdkVersionInfo, logger)
    }

    @Test
    fun testSetUnityMetaDataNotStarted() {
        every { embrace.isStarted() } returns false
        impl.setUnityMetaData("unityVersion", "buildGuid", "unitySdkVersion")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
        }
    }

    @Test
    fun testSetUnityMetaDataNull() {
        every { embrace.isStarted() } returns true
        impl.setUnityMetaData(null, null, "unitySdkVersion")
        assertNull(preferencesService.unityVersionNumber)
        assertNull(preferencesService.unityBuildIdNumber)
        assertNull(preferencesService.unitySdkVersionNumber)
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testLogUnhandledUnityException() {
        every { embrace.isStarted() } returns true
        impl.logUnhandledUnityException("name", "msg", "stack")
        verify(exactly = 1) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Unity exception",
                null,
                null,
                "stack",
                LogExceptionType.UNHANDLED,
                null,
                null,
                "name",
                "msg"
            )
        }
    }

    @Test
    fun testLogHandledUnityException() {
        every { embrace.isStarted() } returns true
        impl.logHandledUnityException("name", "msg", "stack")
        verify(exactly = 1) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Unity exception",
                null,
                null,
                "stack",
                LogExceptionType.HANDLED,
                null,
                null,
                "name",
                "msg"
            )
        }
    }
}
