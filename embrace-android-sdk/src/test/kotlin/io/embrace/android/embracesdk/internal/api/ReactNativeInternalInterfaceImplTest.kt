package io.embrace.android.embracesdk.internal.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.ReactNativeInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSourceImpl
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReactNativeInternalInterfaceImplTest : RobolectricTest() {

    private lateinit var impl: ReactNativeInternalInterfaceImpl
    private lateinit var embrace: EmbraceImpl
    private lateinit var store: FakeKeyValueStore
    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var rnBundleIdTracker: FakeRnBundleIdTracker
    private lateinit var logger: FakeInternalLogger
    private lateinit var context: Context
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo

    @Before
    fun setUp() {
        embrace = mockk(relaxed = true)
        store = FakeKeyValueStore()
        rnBundleIdTracker = FakeRnBundleIdTracker()
        hostedSdkVersionInfo = ReactNativeSdkVersionInfo(store)
        logger = FakeInternalLogger(false)
        context = ApplicationProvider.getApplicationContext()
        bootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            instrumentationModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeInstrumentationModule(ApplicationProvider.getApplicationContext())
            }
        )
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            FakeEmbraceInternalInterface(),
            bootstrapper,
            rnBundleIdTracker,
            hostedSdkVersionInfo,
            logger
        )
    }

    @Test
    fun testSetJavaScriptPatchNumberNotStarted() {
        every { embrace.isStarted } returns false
        impl.setJavaScriptPatchNumber("28.9.1")
        assertEquals(1, logger.sdkNotInitializedMessages.size)
    }

    @Test
    fun testSetJavaScriptPatchNumberNull() {
        every { embrace.isStarted } returns true
        impl.setJavaScriptPatchNumber("123")
        impl.setJavaScriptPatchNumber(null)
        assertEquals("123", store.values().values.single())
    }

    @Test
    fun testSetJavaScriptPatchNumberEmpty() {
        every { embrace.isStarted } returns true
        impl.setJavaScriptPatchNumber("123")
        impl.setJavaScriptPatchNumber("")
        assertEquals("123", store.values().values.single())
    }

    @Test
    fun testSetReactNativeVersionNumberNotStarted() {
        every { embrace.isStarted } returns false
        impl.setReactNativeVersionNumber("0.69.1")
        assertEquals(1, logger.sdkNotInitializedMessages.size)
    }

    @Test
    fun testSetReactNativeVersionNumberNull() {
        every { embrace.isStarted } returns true
        impl.setReactNativeVersionNumber("0.1")
        impl.setReactNativeVersionNumber(null)
        assertEquals("0.1", store.values().values.single())
    }

    @Test
    fun testSetReactNativeVersionNumberEmpty() {
        every { embrace.isStarted } returns true
        impl.setReactNativeVersionNumber("0.1")
        impl.setReactNativeVersionNumber("")
        assertEquals("0.1", store.values().values.single())
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
        assertEquals(1, logger.sdkNotInitializedMessages.size)
    }

    @Test
    fun testSetJavaScriptBundleURLForOtherOTAs() {
        impl = ReactNativeInternalInterfaceImpl(
            embrace,
            FakeEmbraceInternalInterface(),
            bootstrapper,
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
    fun `test RN crash by calling logUnhandledJsException() before handleCrash()`() {
        every { embrace.isStarted } returns true
        bootstrapper.init(ApplicationProvider.getApplicationContext())

        val registry = bootstrapper.instrumentationModule.instrumentationRegistry
        val args = bootstrapper.instrumentationModule.instrumentationArgs
        val dataSource = JvmCrashDataSourceImpl(args)
        registry.add(DataSourceState(factory = { dataSource }))

        impl.logUnhandledJsException("name", "message", "type", "stack")
        dataSource.logUnhandledJvmThrowable(IllegalStateException("Whoops"))

        val destination = args.destination as FakeTelemetryDestination
        val logEvent = destination.logEvents.single()
        assertEquals(EmbType.System.ReactNativeCrash, logEvent.schemaType.telemetryType)
        val lastSentCrashAttributes = logEvent.schemaType.attributes()
        assertEquals(1, destination.logEvents.size)
        assertEquals("Whoops", lastSentCrashAttributes["exception.message"])
        assertEquals("java.lang.IllegalStateException", lastSentCrashAttributes["exception.type"])
        assertEquals(
            "{\"n\":\"name\",\"" +
                "m\":\"message\",\"" +
                "t\":\"type\",\"" +
                "st\":\"" +
                "stack\"}",
            lastSentCrashAttributes["emb.android.react_native_crash.js_exception"]
        )
    }

    @Test
    fun testLogUnhandledJsExceptionNotStarted() {
        every { embrace.isStarted } returns false
        impl.logUnhandledJsException("name", "message", "type", "stack")
        assertEquals(1, logger.sdkNotInitializedMessages.size)
    }
}
