package io.embrace.android.embracesdk.internal.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeRnBundleIdTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.metadata.ReactNativeSdkVersionInfo
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSourceImpl
import io.mockk.mockk
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReactNativeInternalInterfaceImplTest {

    private lateinit var impl: HostedSdkInternalInterfaceImpl
    private lateinit var store: FakeKeyValueStore
    private lateinit var instrumentationModule: FakeInstrumentationModule
    private lateinit var rnBundleIdTracker: FakeRnBundleIdTracker
    private lateinit var logger: FakeInternalLogger
    private lateinit var context: Context
    private lateinit var hostedSdkVersionInfo: HostedSdkVersionInfo

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        rnBundleIdTracker = FakeRnBundleIdTracker()
        hostedSdkVersionInfo = ReactNativeSdkVersionInfo(store)
        logger = FakeInternalLogger(false)
        context = ApplicationProvider.getApplicationContext()
        instrumentationModule = FakeInstrumentationModule(ApplicationProvider.getApplicationContext())
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = mockk(),
            configService = FakeConfigService(),
            resourceSource = FakeEnvelopeResourceSource(),
            instrumentationRegistry = instrumentationModule.instrumentationRegistry,
            serializer = TestPlatformSerializer(),
            rnBundleIdTracker = lazyOf(rnBundleIdTracker),
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            logger = logger,
            telemetryService = FakeInternalTelemetryService(),
        )
    }

    @Test
    fun testSetJavaScriptPatchNumberNull() {
        impl.setJavaScriptPatchNumber("123")
        impl.setJavaScriptPatchNumber(null)
        assertEquals("123", store.values().values.single())
    }

    @Test
    fun testSetJavaScriptPatchNumberEmpty() {
        impl.setJavaScriptPatchNumber("123")
        impl.setJavaScriptPatchNumber("")
        assertEquals("123", store.values().values.single())
    }

    @Test
    fun testSetReactNativeVersionNumberNull() {
        impl.setReactNativeVersionNumber("0.1")
        impl.setReactNativeVersionNumber(null)
        assertEquals("0.1", store.values().values.single())
    }

    @Test
    fun testSetReactNativeVersionNumberEmpty() {
        impl.setReactNativeVersionNumber("0.1")
        impl.setReactNativeVersionNumber("")
        assertEquals("0.1", store.values().values.single())
    }

    @Test
    fun testSetJavaScriptBundleURL() {
        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        assertEquals("index.android.bundle", rnBundleIdTracker.fakeReactNativeBundleId)
    }

    @Test
    fun testSetJavaScriptBundleURLForOtherOTAs() {
        impl = HostedSdkInternalInterfaceImpl(
            logsApi = mockk(),
            configService = FakeConfigService(),
            resourceSource = FakeEnvelopeResourceSource(),
            instrumentationRegistry = instrumentationModule.instrumentationRegistry,
            serializer = TestPlatformSerializer(),
            rnBundleIdTracker = lazyOf(rnBundleIdTracker),
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            logger = logger,
            telemetryService = FakeInternalTelemetryService(),
        )

        impl.setJavaScriptBundleUrl(context, "index.android.bundle")
        // Test that the metadata service was called with the correct parameters
        assertEquals("index.android.bundle", rnBundleIdTracker.fakeReactNativeBundleId)
        assertEquals(null, rnBundleIdTracker.forceUpdate)
    }

    @Test
    fun `test RN crash by calling logUnhandledJsException() before handleCrash()`() {
        val registry = instrumentationModule.instrumentationRegistry
        val args = instrumentationModule.instrumentationArgs
        val dataSource = JvmCrashDataSourceImpl(args)
        registry.add { dataSource }

        impl.logUnhandledJsException("name", "message", "type", "stack")
        dataSource.logUnhandledJvmThrowable(IllegalStateException("Whoops"))

        val destination = args.destination as FakeTelemetryDestination
        val logEvent = destination.logEvents.single()
        assertEquals(EmbType.System.ReactNativeCrash, logEvent.schemaType.telemetryType)
        val lastSentCrashAttributes = logEvent.schemaType.attributes()
        assertEquals(1, destination.logEvents.size)
        assertEquals("Whoops", lastSentCrashAttributes[ExceptionAttributes.EXCEPTION_MESSAGE])
        assertEquals("java.lang.IllegalStateException", lastSentCrashAttributes[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals(
            "{\"n\":\"name\",\"" +
                "m\":\"message\",\"" +
                "t\":\"type\",\"" +
                "st\":\"" +
                "stack\"}",
            lastSentCrashAttributes[embAndroidReactNativeCrashJsException],
        )
    }
}
