package io.embrace.android.embracesdk.testframework

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.EmbraceHooks
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBaseUrlConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.export.FilteredLogExporter
import io.embrace.android.embracesdk.testframework.export.FilteredSpanExporter
import io.embrace.android.embracesdk.testframework.server.FakeApiServer
import java.io.File
import okhttp3.Protocol
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource

/**
 * A [org.junit.Rule] that is responsible for setting up and tearing down the Embrace SDK for use in
 * component/integration testing. Test cases must be run with a test runner that is compatible with Robolectric.
 *
 * The SDK instance exposed is almost like the one used in production other than the 3 modules that have
 * to faked at least in part in order for this to be useful in tests:
 *
 * 1) [CoreModule]: Requires [FakeCoreModule] to fake Android system objects like [Application], [Context],
 * and resources that are normally not available in a JUnit test environment. Also, a [FakeClock] is used
 * so we can control time to control timing and execution manually rather than waiting for normal
 * time to pass.
 *
 * 2) [EssentialServiceModule]: Requires an instance of [EssentialServiceModuleImpl] that uses a [FakeConfigService]
 * to allow the appropriate feature flags be set to suit the test case. Everything else in the
 * module is otherwise real unless provided by [FakeCoreModule]
 *
 * 3) [DeliveryModule]: Requires [FakeDeliveryModule] so that a [FakeDeliveryService] can be used in order
 * for payloads normally sent to the server to be inspected to verify the correctness of the data produced.
 * Everything else in [FakeDeliveryModule] is otherwise real unless provided by [FakeCoreModule]
 *
 * The modules instantiated by default reference each other, e.g. the same instance of [FakeCoreModule] is used
 * in all the other modules. This means modifications of one object within a module will be reflected in the
 * other modules, e.g. [InitModule.clock]. This allows the system to behave as one unit.
 *
 * While it is possible to override the default behavior of the [Embrace] instance in the Rule by passing in
 * a custom supplier to create an instance of [EmbraceSetupInterface] with custom overridden constructor parameters, be
 * careful when you do so by passing in overridden module instances as that might break the integrity of the
 * "one instance" guarantee of using the modules created by default (besides the fakes).
 *
 * It is also possible to access internal modules & dependencies that are not exposed via the public API.
 * For example, it is possible to access the [FakeDeliveryModule] to get event/session payloads the SDK sent.
 *
 * In general, it is recommended to use functions declared in PayloadRetrievalExtensions.kt that retrieve
 * this useful information for you. If it's not possible to get information from the SDK and you
 * need it for a test, please consider adding a new function to PayloadRetrievalExtensions.kt so that others
 * find it easier to write tests in the future.
 *
 * Because there are parts of the [Embrace] instance being tested that are using fakes, unless you are careful,
 * do not use this to verify code paths that are overridden by fakes. For example, do not use this rule to
 * verify the default config settings because what's in use is a [FakeConfigService], which is not used in
 * production.
 */
internal class IntegrationTestRule(
    private val embraceSetupInterfaceSupplier: Provider<EmbraceSetupInterface> = { EmbraceSetupInterface() },
) : ExternalResource() {

    /**
     * Instance of the test harness that is recreating on every test iteration
     */
    lateinit var setup: EmbraceSetupInterface

    /**
     * Used to perform actions on the Embrace class under test
     */
    private lateinit var action: EmbraceActionInterface

    /**
     * Used to perform actions on the payload generated by a test
     */
    private lateinit var payloadAssertion: EmbracePayloadAssertionInterface

    lateinit var preSdkStart: EmbracePreSdkStartInterface
    private lateinit var otelAssertion: EmbraceOtelExportAssertionInterface
    private lateinit var spanExporter: FilteredSpanExporter
    private lateinit var logExporter: FilteredLogExporter
    private lateinit var embraceImpl: EmbraceImpl
    private lateinit var baseUrl: String

    lateinit var bootstrapper: ModuleInitBootstrapper

    /**
     * Runs a test case. The test case should be separated into 3 parts: setup, action, and
     * assertions. This aims to enforce the better compartmentalisation & reuse of test code within
     * the integration test suite.
     */
    fun runTest(
        startSdk: Boolean = true,
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        persistedRemoteConfig: RemoteConfig = RemoteConfig(),
        serverResponseConfig: RemoteConfig = persistedRemoteConfig,
        expectSdkToStart: Boolean = startSdk,
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        preSdkStartAction: EmbracePreSdkStartInterface.() -> Unit = {},
        testCaseAction: EmbraceActionInterface.() -> Unit,
        assertAction: EmbracePayloadAssertionInterface.() -> Unit = {},
        otelExportAssertion: EmbraceOtelExportAssertionInterface.() -> Unit = {},
    ) {
        setup = embraceSetupInterfaceSupplier()
        var apiServer: FakeApiServer? = null
        val deliveryTracer = DeliveryTracer()

        if (setup.useMockWebServer) {
            apiServer = FakeApiServer(serverResponseConfig, deliveryTracer)
            val server: MockWebServer = MockWebServer().apply {
                protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
                dispatcher = apiServer
                start()
            }
            baseUrl = server.url("api").toString()
        }

        preSdkStart = EmbracePreSdkStartInterface(setup)
        bootstrapper = setup.createBootstrapper(prepareConfig(instrumentedConfig), deliveryTracer)
        action = EmbraceActionInterface(setup, bootstrapper)
        payloadAssertion = EmbracePayloadAssertionInterface(bootstrapper, apiServer)
        spanExporter = FilteredSpanExporter()
        logExporter = FilteredLogExporter()
        otelAssertion = EmbraceOtelExportAssertionInterface(spanExporter, logExporter)

        setupAction(setup)
        with(setup) {
            embraceImpl = EmbraceImpl(bootstrapper)
            EmbraceHooks.setImpl(embraceImpl)
            preSdkStartAction(preSdkStart)
            embraceImpl.addSpanExporter(spanExporter)
            embraceImpl.addLogRecordExporter(logExporter)

            // persist config here before the SDK starts up
            persistConfig(persistedRemoteConfig)

            if (startSdk) {
                embraceImpl.start(overriddenCoreModule.context)
                assertEquals(
                    "SDK did not start in integration test.",
                    expectSdkToStart,
                    embraceImpl.isStarted
                )
            }
        }
        testCaseAction(action)
        assertAction(payloadAssertion)
        spanExporter.failOnDuplicate()
        otelExportAssertion(otelAssertion)
    }

    /**
     * Writes a config response to the expected location on disk so the SDK can read it.
     */
    private fun persistConfig(persistedRemoteConfig: RemoteConfig) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val storageDir = File(ctx.filesDir, "embrace_remote_config").apply {
            mkdirs()
        }
        File(storageDir, "etag").writeText("persisted_etag")
        val responseFile = File(storageDir, "most_recent_response")

        responseFile.outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(persistedRemoteConfig, RemoteConfig::class.java, stream)
        }
    }

    private fun prepareConfig(instrumentedConfig: FakeInstrumentedConfig) =
        when {
            setup.useMockWebServer -> instrumentedConfig.copy(
                baseUrls = FakeBaseUrlConfig(configImpl = baseUrl, dataImpl = baseUrl)
            )

            else -> instrumentedConfig
        }

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {

    }

    /**
     * Teardown the Embrace SDK, closing any resources as required
     */
    override fun after() {
        embraceImpl.stop()
    }

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L
    }
}
