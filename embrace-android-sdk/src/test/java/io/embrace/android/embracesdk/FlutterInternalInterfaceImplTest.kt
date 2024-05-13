package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class FlutterInternalInterfaceImplTest {

    private lateinit var impl: FlutterInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var logger: EmbLogger
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo
    private lateinit var fakePreferencesService: FakePreferenceService

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        fakePreferencesService = FakePreferenceService(
            dartSdkVersion = "fakeDartVersion",
            embraceFlutterSdkVersion = "fakeFlutterSdkVersion"
        )
        hostedSdkVersionInfo = HostedSdkVersionInfo(
            fakePreferencesService,
            Embrace.AppFramework.FLUTTER
        )
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

    @Test
    fun testLogUnhandledDartException() {
        every { embrace.isStarted } returns true
        impl.logUnhandledDartException("stack", "exception name", "message", "ctx", "lib")
        verify(exactly = 1) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Dart error",
                null,
                null,
                "stack",
                LogExceptionType.UNHANDLED,
                "ctx",
                "lib",
                "exception name",
                "message"
            )
        }
    }

    @Test
    fun testLogHandledDartException() {
        every { embrace.isStarted } returns true
        impl.logHandledDartException("stack", "exception name", "message", "ctx", "lib")
        verify(exactly = 1) {
            embrace.logMessage(
                EventType.ERROR_LOG,
                "Dart error",
                null,
                null,
                "stack",
                LogExceptionType.HANDLED,
                "ctx",
                "lib",
                "exception name",
                "message"
            )
        }
    }
}
