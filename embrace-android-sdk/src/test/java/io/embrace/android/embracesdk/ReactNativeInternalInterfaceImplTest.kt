package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework.FLUTTER
import io.embrace.android.embracesdk.Embrace.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.JsException
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ReactNativeInternalInterfaceImplTest {

    private lateinit var impl: ReactNativeInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var preferencesService: PreferencesService
    private lateinit var crashService: CrashService
    private lateinit var metadataService: FakeMetadataService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var context: Context

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        preferencesService = FakePreferenceService()
        crashService = mockk(relaxed = true)
        metadataService = FakeMetadataService()
        logger = mockk(relaxed = true)
        context = mockContext()
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            REACT_NATIVE,
            preferencesService,
            crashService,
            metadataService,
            logger
        )
    }

    @Test
    fun testSetJavaScriptPatchNumber() {
        every { embrace.isStarted } returns true
        impl.setJavaScriptPatchNumber("28.9.1")
        assertEquals("28.9.1", preferencesService.javaScriptPatchNumber)
    }

    @Test
    fun testSetJavaScriptPatchNumberNotStarted() {
        every { embrace.isStarted } returns false
        impl.setJavaScriptPatchNumber("28.9.1")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }

    @Test
    fun testSetJavaScriptPatchNumberNull() {
        every { embrace.isStarted } returns true
        preferencesService.javaScriptPatchNumber = "123"
        impl.setJavaScriptPatchNumber(null)
        assertEquals("123", preferencesService.javaScriptPatchNumber)
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testSetJavaScriptPatchNumberEmpty() {
        every { embrace.isStarted } returns true
        preferencesService.javaScriptPatchNumber = "123"
        impl.setJavaScriptPatchNumber("")
        assertEquals("123", preferencesService.javaScriptPatchNumber)
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testSetReactNativeVersionNumberNotStarted() {
        every { embrace.isStarted } returns false
        impl.setReactNativeVersionNumber("0.69.1")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }

    @Test
    fun testSetReactNativeVersionNumberNull() {
        every { embrace.isStarted } returns true
        preferencesService.reactNativeVersionNumber = "0.1"
        impl.setReactNativeVersionNumber(null)
        assertEquals("0.1", preferencesService.reactNativeVersionNumber)
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testSetReactNativeVersionNumberEmpty() {
        every { embrace.isStarted } returns true
        preferencesService.reactNativeVersionNumber = "0.1"
        impl.setReactNativeVersionNumber("")
        assertEquals("0.1", preferencesService.reactNativeVersionNumber)
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testSetJavaScriptBundleURL() {
        every { embrace.isStarted } returns true
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        assertEquals("index.android.bundle", metadataService.fakeReactNativeBundleId)
    }

    @Test
    fun testSetJavaScriptBundleURLNotStarted() {
        every { embrace.isStarted } returns false
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }

    @Test
    fun testSetJavaScriptBundleURLWrongFramework() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            FLUTTER,
            preferencesService,
            crashService,
            metadataService,
            logger
        )

        every { embrace.isStarted } returns true
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        verify(exactly = 1) {
            logger.logError(any())
        }
    }

    @Test
    fun testSetCacheableJavaScriptBundleUrl() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            REACT_NATIVE,
            preferencesService,
            crashService,
            metadataService,
            logger
        )

        every { embrace.isStarted } returns true
        impl.setCacheableJavaScriptBundleUrl(context, "index.android.bundle", true)
        // Test that the metadata service was called with the correct parameters
        assertEquals("index.android.bundle", metadataService.fakeReactNativeBundleId)
        assertEquals(true, metadataService.forceUpdate)
    }

    @Test
    fun testSetJavaScriptBundleURLForOtherOTAs() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            REACT_NATIVE,
            preferencesService,
            crashService,
            metadataService,
            logger
        )

        every { embrace.isStarted } returns true
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        // Test that the metadata service was called with the correct parameters
        assertEquals("index.android.bundle", metadataService.fakeReactNativeBundleId)
        assertEquals(null, metadataService.forceUpdate)
    }

    @Test
    fun testLogUnhandledJsException() {
        every { embrace.isStarted } returns true
        impl.logUnhandledJsException("name", "message", "type", "stack")

        val captor = slot<JsException>()
        verify(exactly = 1) {
            crashService.logUnhandledJsException(capture(captor))
        }
        with(captor.captured) {
            assertEquals("name", name)
            assertEquals("message", message)
            assertEquals("type", type)
            assertEquals("stack", stacktrace)
        }
    }

    @Test
    fun testLogUnhandledJsExceptionNotStarted() {
        every { embrace.isStarted } returns false
        impl.logUnhandledJsException("name", "message", "type", "stack")
        verify(exactly = 1) {
            logger.logSDKNotInitialized(any())
        }
    }
}
