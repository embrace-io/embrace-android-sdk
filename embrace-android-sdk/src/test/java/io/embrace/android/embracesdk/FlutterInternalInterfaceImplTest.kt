package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class FlutterInternalInterfaceImplTest {

    private lateinit var impl: FlutterInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var metadataService: FakeMetadataService

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        metadataService = FakeMetadataService()
        logger = mockk(relaxed = true)
        impl = FlutterInternalInterfaceImpl(embrace, mockk(), metadataService, logger)
    }

    @Test
    fun testSetFlutterSdkVersion() {
        every { embrace.isStarted } returns true
        impl.setEmbraceFlutterSdkVersion("2.12")
        assertEquals("2.12", metadataService.fakeFlutterSdkVersion)
    }

    @Test
    fun testSetFlutterSdkVersionNotStarted() {
        every { embrace.isStarted } returns false
        impl.setEmbraceFlutterSdkVersion("2.12")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }

    @Test
    fun testSetFlutterSdkVersionNull() {
        every { embrace.isStarted } returns true
        impl.setEmbraceFlutterSdkVersion(null)
        assertEquals("fakeFlutterSdkVersion", metadataService.fakeFlutterSdkVersion)
    }

    @Test
    fun testSetDartSdkVersion() {
        every { embrace.isStarted } returns true
        impl.setDartVersion("2.12")
        assertEquals("2.12", metadataService.fakeDartVersion)
    }

    @Test
    fun testSetDartVersionNotStarted() {
        every { embrace.isStarted } returns false
        impl.setDartVersion("2.12")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }

    @Test
    fun testSetDartVersionNull() {
        every { embrace.isStarted } returns true
        impl.setDartVersion(null)
        assertEquals("fakeDartVersion", metadataService.fakeDartVersion)
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
