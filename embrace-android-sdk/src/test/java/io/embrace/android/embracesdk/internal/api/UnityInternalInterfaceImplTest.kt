package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.internal.api.delegate.UnityInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
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
        assertNull(preferencesService.unityVersionNumber)
        assertNull(preferencesService.unityBuildIdNumber)
        assertNull(preferencesService.unitySdkVersionNumber)
    }

    @Test
    fun testLogUnhandledUnityException() {
        every { embrace.isStarted } returns true
        impl.logUnhandledUnityException("name", "msg", "stack")
        verify(exactly = 1) {
            embrace.logMessage(
                severity = Severity.ERROR,
                message = "Unity exception",
                customStackTrace = "stack",
                logExceptionType = LogExceptionType.UNHANDLED,
                exceptionName = "name",
                exceptionMessage = "msg"
            )
        }
    }

    @Test
    fun testLogHandledUnityException() {
        every { embrace.isStarted } returns true
        impl.logHandledUnityException("name", "msg", "stack")
        verify(exactly = 1) {
            embrace.logMessage(
                severity = Severity.ERROR,
                message = "Unity exception",
                customStackTrace = "stack",
                logExceptionType = LogExceptionType.HANDLED,
                exceptionName = "name",
                exceptionMessage = "msg"
            )
        }
    }
}
