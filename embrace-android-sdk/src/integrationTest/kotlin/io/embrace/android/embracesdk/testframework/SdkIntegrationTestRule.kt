
package io.embrace.android.embracesdk.testframework

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeBaseUrlConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServicesSupplier
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.instance.SdkState
import io.embrace.android.embracesdk.internal.instance.SdkStateHolder
import io.embrace.android.embracesdk.internal.instance.createSdkStateHolder
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePreSdkStartInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.export.FilteredLogExporter
import io.embrace.android.embracesdk.testframework.export.FilteredSpanExporter
import io.embrace.android.embracesdk.testframework.server.FakeApiServer
import io.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordExporter
import io.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanExporter
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * A [org.junit.Rule] that is responsible for setting up and tearing down the Embrace SDK for use in
 * component/integration testing. Test cases must be run with a test runner that is compatible with Robolectric.
 *
 * The SDK instance exposed is almost like the one used in production other than the 3 areas that have
 * to be faked at least in part in order for this to be useful in tests:
 *
 * 1) Core: uses [fakeContext] to fake Android system objects like [Application], [Context],
 * and resources that are normally not available in a JUnit test environment. Also, a [FakeClock] is used
 * so we can control time to control timing and execution manually rather than waiting for normal
 * time to pass. `SharedPreferences` are held in memory by [InMemorySharedPreferences] rather than written to disk.
 *
 * 2) Essential services: these use a [FakeConfigService] to allow the appropriate feature flags be set to suit
 * the test case. The lifecycle tracker and network connectivity service are replaced via [SdkOverrides], and
 * everything else is otherwise real
 *
 * 3) Delivery: payloads are sent to a local [MockWebServer] and recorded by a [DeliveryTracer], so that
 * payloads normally sent to the server can be inspected to verify the correctness of the data produced.
 * The delivery services are otherwise real unless replaced via [SdkOverrides].
 *
 * The services instantiated by default reference each other, e.g. the same context is used
 * by all the other services. This means modifications of one object will be reflected in the
 * other services, e.g. the [FakeClock]. This allows the system to behave as one unit.
 *
 * While it is possible to override the default behavior of the [Embrace] instance in the Rule by passing in
 * a custom supplier to create an instance of [EmbraceSetupInterface] with custom overridden constructor parameters, be
 * careful when you do so by passing in overridden module instances as that might break the integrity of the
 * "one instance" guarantee of using the modules created by default (besides the fakes).
 *
 * Each test gets its own [SdkStateHolder], and test code calls the SDK through [SdkStateHolder.api]. The public
 * [Embrace] object delegates to that same API, so tests exercise the production lifecycle and dispatch logic,
 * without the pain of dealing with a singleton in parallelized unit tests.
 *
 * It is also possible to access internal modules & dependencies that are not exposed via the public API.
 * For example, it is possible to access [services] to inspect the delivery services the SDK used.
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
internal class SdkIntegrationTestRule(

    /**
     * The opentelemetry-kotlin implementation to run against.
     */
    val otelSdkMode: OtelSdkMode,
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
    private lateinit var sdkStateHolder: SdkStateHolder
    private lateinit var baseUrl: String
    private lateinit var server: MockWebServer

    private lateinit var configLoadServicesSupplier: ConfigLoadServicesSupplier
    /**
     * The SDK services that tests inspect, captured as the SDK under test constructs them.
     */
    val services: CapturedServices
        get() = setup.services

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
        serverResponseConfigJson: String? = null,
        serverResponseConfigEtag: String = "server_etag_value",
        expectSdkToStart: Boolean = startSdk,
        expectedSdkState: SdkState = if (expectSdkToStart) SdkState.STARTED else SdkState.DISABLED,
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        preSdkStartAction: EmbracePreSdkStartInterface.() -> Unit = {},
        testCaseAction: EmbraceActionInterface.() -> Unit,
        assertAction: EmbracePayloadAssertionInterface.() -> Unit = {},
        otelExportAssertion: EmbraceOtelExportAssertionInterface.() -> Unit = {},
    ) {
        val localConfig = otelSdkMode.applyTo(instrumentedConfig)
        val persistedConfig = otelSdkMode.applyTo(persistedRemoteConfig)
        val serverConfig = otelSdkMode.applyTo(serverResponseConfig)
        setup = embraceSetupInterfaceSupplier()
        val deliveryTracer = DeliveryTracer()
        val apiServer = FakeApiServer(serverConfig, deliveryTracer, serverResponseConfigJson, serverResponseConfigEtag)
        server = MockWebServer().apply {
            protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            dispatcher = apiServer
            start()
        }
        baseUrl = server.url("api").toString()

        preSdkStart = EmbracePreSdkStartInterface(setup) { sdkStateHolder.api }
        configLoadServicesSupplier = setup.createConfigLoadServicesSupplier(
            localConfig.copy(
                baseUrls = FakeBaseUrlConfig(configImpl = baseUrl, dataImpl = baseUrl)
            ),
        )
        action = EmbraceActionInterface(
            setup,
            { services.instrumentationRegistry },
            { services.eventBus },
            { sdkStateHolder.api },
            { sdkStateHolder.state },
        )
        payloadAssertion = EmbracePayloadAssertionInterface(
            setup.initServices.jsonSerializer,
            deliveryTracer,
            apiServer,
        ) { sdkStateHolder.state }
        spanExporter = FilteredSpanExporter()
        logExporter = FilteredLogExporter()
        otelAssertion = EmbraceOtelExportAssertionInterface(spanExporter, logExporter)

        setupAction(setup)
        with(setup) {
            sdkStateHolder = createStateHolder(deliveryTracer)
            preSdkStartAction(preSdkStart)
            //TODO: Filtered span and log exporters should be migrated to Kotlin.
            sdkStateHolder.api.addSpanExporter(spanExporter.toOtelKotlinSpanExporter())
            sdkStateHolder.api.addLogRecordExporter(logExporter.toOtelKotlinLogRecordExporter())

            // persist config here before the SDK starts up: the SDK reads it during start()
            persistConfig(persistedConfig)

            if (startSdk) {
                sdkStateHolder.api.start(ApplicationProvider.getApplicationContext())
                assertEquals(
                    "SDK did not start in integration test.",
                    expectSdkToStart,
                    sdkStateHolder.api.isStarted
                )
                assertEquals(
                    "SDK state holder did not reach the expected state.",
                    expectedSdkState,
                    sdkStateHolder.state
                )
                if (sdkStateHolder.api.isStarted) {
                    assertEquals(
                        "SDK did not select the $otelSdkMode opentelemetry-kotlin implementation.",
                        otelSdkMode.useKotlinSdk,
                        services.otelSdkWrapper.useKotlinSdk,
                    )
                    // a start that fails releases the worker threads, so there is nothing to await
                    awaitAsyncInstrumentation()
                }
            }
        }
        testCaseAction(action)
        setup.runDueLogBatchChecks()
        drainExternalExports()
        assertAction(payloadAssertion)
        spanExporter.failOnDuplicate()
        otelExportAssertion(otelAssertion)
    }

    private fun drainExternalExports() {
        val otelSdkConfig = services.otelSdkConfigOrNull ?: return
        runBlocking {
            otelSdkConfig.spanProcessor.forceFlush()
            otelSdkConfig.logRecordProcessor.forceFlush()
        }
    }

    /**
     * Writes a config response to the expected location on disk so the SDK can read it.
     */
    private fun persistConfig(persistedRemoteConfig: RemoteConfig) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val storageDir = File(ctx.filesDir, PersistedConfig.STORAGE_DIR_NAME).apply {
            mkdirs()
        }
        File(storageDir, PersistedConfig.ETAG_FILE_NAME).writeText("persisted_etag")
        val responseFile = File(storageDir, PersistedConfig.CONFIG_RESPONSE_FILE_NAME)

        responseFile.outputStream().buffered().use { stream ->
            TestPlatformSerializer().toJson(persistedRemoteConfig, RemoteConfig.serializer(), stream)
        }
    }

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        EmbTrace.durationTracker.reset()
        useLightweightActivityTheme()
    }

    /**
     * Activities get the default Material theme otherwise, so every Activity.onCreate inflates a full window decor
     * including an action bar, which is about a quarter of the CPU time of the suite. A framework theme
     * without an action bar or activity transitions makes each simulated activity about 4x cheaper.
     */
    private fun useLightweightActivityTheme() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = shadowOf(ctx.packageManager).getInternalMutablePackageInfo(ctx.packageName)
        packageInfo.applicationInfo?.theme = android.R.style.Theme_NoTitleBar
    }

    /**
     * Teardown the Embrace SDK, closing any resources as required
     */
    override fun after() {
        if (::sdkStateHolder.isInitialized) {
            sdkStateHolder.close()
        }
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    private fun createStateHolder(deliveryTracer: DeliveryTracer): SdkStateHolder {
        return createSdkStateHolder(
            preStartServices = setup.preStartServices,
            configLoadServicesSupplier = configLoadServicesSupplier,
            sdkInitResourceUsageTrackerSupplier = setup::fakeSdkInitResourceUsageTracker,
            overrides = setup.createSdkOverrides(deliveryTracer),
        )
    }

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L
        const val DEFAULT_EXPIRED_USER_SESSION_ID = "ef7ddb7cc8dd40a1be7b5cce5dc49d95"
        const val DEFAULT_DEAD_SESSION_PART_ID = "dde17b0916354843bf2fbead12b028a9"
    }
}
