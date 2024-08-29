package io.embrace.android.embracesdk.internal.api

import android.content.Context
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.internal.api.delegate.ReactNativeInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
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
    private lateinit var rnBundleIdTracker: FakeRnBundleIdTracker
    private lateinit var logger: EmbLogger
    private lateinit var context: Context
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        preferencesService = FakePreferenceService()
        crashService = mockk(relaxed = true)
        rnBundleIdTracker = FakeRnBundleIdTracker()
        hostedSdkVersionInfo = HostedSdkVersionInfo(
            preferencesService,
            AppFramework.REACT_NATIVE
        )
        logger = mockk(relaxed = true)
        context = mockContext()
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            crashService,
            rnBundleIdTracker,
            hostedSdkVersionInfo,
            logger
        )
    }

    @Test
    fun testSetJavaScriptPatchNumberNotStarted() {
        every { embrace.isStarted } returns false
        impl.setJavaScriptPatchNumber("28.9.1")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
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
            logger.logSdkNotInitialized(any())
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
        assertEquals("index.android.bundle", rnBundleIdTracker.fakeReactNativeBundleId)
    }

    @Test
    fun testSetJavaScriptBundleURLNotStarted() {
        every { embrace.isStarted } returns false
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        verify(exactly = 1) {
            logger.logSdkNotInitialized(any())
        }
    }

    @Test
    fun testSetCacheableJavaScriptBundleUrl() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            crashService,
            rnBundleIdTracker,
            hostedSdkVersionInfo,
            logger
        )

        every { embrace.isStarted } returns true
        impl.setCacheableJavaScriptBundleUrl(context, "index.android.bundle", true)
        // Test that the metadata service was called with the correct parameters
        assertEquals("index.android.bundle", rnBundleIdTracker.fakeReactNativeBundleId)
        assertEquals(true, rnBundleIdTracker.forceUpdate)
    }

    @Test
    fun testSetJavaScriptBundleURLForOtherOTAs() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            mockk(),
            crashService,
            rnBundleIdTracker,
            hostedSdkVersionInfo,
            logger
        )

        every { embrace.isStarted } returns true
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        // Test that the metadata service was called with the correct parameters
        assertEquals("index.android.bundle", rnBundleIdTracker.fakeReactNativeBundleId)
        assertEquals(null, rnBundleIdTracker.forceUpdate)
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
            logger.logSdkNotInitialized(any())
        }
    }
}
