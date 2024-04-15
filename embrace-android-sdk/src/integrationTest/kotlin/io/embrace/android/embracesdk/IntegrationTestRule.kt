package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.fakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.AndroidServicesModuleImpl
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.injection.StorageModuleImpl
import io.embrace.android.embracesdk.injection.SystemServiceModule
import io.embrace.android.embracesdk.injection.SystemServiceModuleImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
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
 * a custom supplier to create an instance of [Harness] with custom overridden constructor parameters, be
 * careful when you do so by passing in overridden module instances as that might break the integrity of the
 * "one instance" guarantee of using the modules created by default (besides the fakes).
 *
 * It is also possible to access internal modules & dependencies that are not exposed via the public API.
 * For example, it is possible to access the [FakeDeliveryModule] to get event/session payloads the SDK sent.
 *
 * In general, it is recommended to use functions declared in IntegrationTestRuleExtensions.kt that retrieve
 * this useful information for you. If it's not possible to get information from the SDK and you
 * need it for a test, please consider adding a new function to IntegrationTestRuleExtensions.kt so that others
 * find it easier to write tests in the future.
 *
 * Because there are parts of the [Embrace] instance being tested that are using fakes, unless you are careful,
 * do not use this to verify code paths that are overridden by fakes. For example, do not use this rule to
 * verify the default config settings because what's in use is a [FakeConfigService], which is not used in
 * production.
 */
internal class IntegrationTestRule(
    private val harnessSupplier: Provider<Harness> = { Harness() }
) : ExternalResource() {
    /**
     * The [Embrace] instance that can be used for testing
     */
    val embrace = Embrace.getInstance()

    /**
     * Instance of the test harness that is recreating on every test iteration
     */
    lateinit var harness: Harness

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {
        harness = harnessSupplier.invoke()
        with(harness) {
            val embraceImpl = EmbraceImpl(
                ModuleInitBootstrapper(
                    initModule = overriddenInitModule,
                    openTelemetryModule = overriddenInitModule.openTelemetryModule,
                    coreModuleSupplier = { _, _, _ -> overriddenCoreModule },
                    systemServiceModuleSupplier = { _, _ -> systemServiceModule },
                    androidServicesModuleSupplier = { _, _, _ -> androidServicesModule },
                    workerThreadModuleSupplier = { _ -> overriddenWorkerThreadModule },
                    storageModuleSupplier = { _, _, _ -> storageModule },
                    essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _, _ -> essentialServiceModule },
                    dataSourceModuleSupplier = { _, _, _, _, _, _ -> dataSourceModule },
                    dataCaptureServiceModuleSupplier = { _, _, _, _, _, _, _, _ -> dataCaptureServiceModule },
                    deliveryModuleSupplier = { _, _, _, _, _ -> overriddenDeliveryModule },
                )
            )
            Embrace.setImpl(embraceImpl)
            if (startImmediately) {
                embraceImpl.startInternal(overriddenCoreModule.context, enableIntegrationTesting, appFramework) { overriddenConfigService }
            }
        }
    }

    /**
     * Teardown the Embrace SDK, closing any resources as required
     */
    override fun after() {
        Embrace.getImpl().stop()
    }

    fun startSdk(
        context: Context = harness.overriddenCoreModule.context,
        appFramework: AppFramework = harness.appFramework,
        configServiceProvider: Provider<ConfigService> = { harness.overriddenConfigService }
    ) {
        Embrace.getImpl().startInternal(context, false, appFramework, configServiceProvider)
    }

    /**
     * Test harness for which an instance is generated each test run and provided to the test by the Rule
     */
    internal class Harness(
        currentTimeMs: Long = DEFAULT_SDK_START_TIME_MS,
        val enableIntegrationTesting: Boolean = false,
        val appFramework: Embrace.AppFramework = Embrace.AppFramework.NATIVE,
        val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
        val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock),
        val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
        val overriddenCoreModule: FakeCoreModule = FakeCoreModule(appFramework = appFramework, logger = overriddenInitModule.logger),
        val overriddenWorkerThreadModule: WorkerThreadModule = WorkerThreadModuleImpl(overriddenInitModule),
        val overriddenConfigService: FakeConfigService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
            sdkModeBehavior = fakeSdkModeBehavior(
                isDebug = overriddenCoreModule.isDebug,
                localCfg = { DEFAULT_LOCAL_CONFIG }
            ),
            networkBehavior = fakeNetworkBehavior(
                localCfg = { DEFAULT_SDK_LOCAL_CONFIG },
                remoteCfg = { DEFAULT_SDK_REMOTE_CONFIG }
            ),
            networkSpanForwardingBehavior = fakeNetworkSpanForwardingBehavior {
                NetworkSpanForwardingRemoteConfig(pctEnabled = 100.0f)
            },
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                remoteCfg = {
                    DEFAULT_SDK_REMOTE_CONFIG.copy(
                        // disable thermal status capture as it interferes with unit tests
                        dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.0f)
                    )
                }
            )
        ),
        val systemServiceModule: SystemServiceModule =
            SystemServiceModuleImpl(
                coreModule = overriddenCoreModule
            ),
        val androidServicesModule: AndroidServicesModule = AndroidServicesModuleImpl(
            initModule = overriddenInitModule,
            coreModule = overriddenCoreModule,
            workerThreadModule = overriddenWorkerThreadModule,
        ),
        val storageModule: StorageModule = StorageModuleImpl(
            initModule = overriddenInitModule,
            workerThreadModule = overriddenWorkerThreadModule,
            coreModule = overriddenCoreModule,
        ),
        val essentialServiceModule: EssentialServiceModule =
            EssentialServiceModuleImpl(
                initModule = overriddenInitModule,
                openTelemetryModule = overriddenInitModule.openTelemetryModule,
                coreModule = overriddenCoreModule,
                workerThreadModule = overriddenWorkerThreadModule,
                systemServiceModule = systemServiceModule,
                androidServicesModule = androidServicesModule,
                storageModule = storageModule,
                customAppId = null,
                enableIntegrationTesting = enableIntegrationTesting,
                configServiceProvider = { overriddenConfigService }
            ),
        val dataSourceModule: DataSourceModule = DataSourceModuleImpl(
            initModule = overriddenInitModule,
            otelModule = overriddenOpenTelemetryModule,
            essentialServiceModule = essentialServiceModule,
            systemServiceModule = systemServiceModule,
            androidServicesModule = androidServicesModule,
            workerThreadModule = overriddenWorkerThreadModule,
        ),
        val dataCaptureServiceModule: DataCaptureServiceModule =
            DataCaptureServiceModuleImpl(
                initModule = overriddenInitModule,
                openTelemetryModule = overriddenInitModule.openTelemetryModule,
                coreModule = overriddenCoreModule,
                systemServiceModule = systemServiceModule,
                essentialServiceModule = essentialServiceModule,
                workerThreadModule = overriddenWorkerThreadModule,
                dataSourceModule = dataSourceModule
            ),
        val overriddenDeliveryModule: FakeDeliveryModule =
            FakeDeliveryModule(
                deliveryService = FakeDeliveryService(),
            ),
        val startImmediately: Boolean = true
    )

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L

        fun newHarness(startImmediately: Boolean): Harness {
            return Harness(startImmediately = startImmediately)
        }

        private val DEFAULT_SDK_LOCAL_CONFIG = SdkLocalConfig(
            networking = NetworkLocalConfig(
                enableNativeMonitoring = false
            ),
            betaFeaturesEnabled = false
        )

        private val DEFAULT_SDK_REMOTE_CONFIG = RemoteConfig(
            disabledUrlPatterns = setOf("dontlogmebro.pizza"),
            networkCaptureRules = setOf(
                NetworkCaptureRuleRemoteConfig(
                    id = "test",
                    duration = 10000,
                    method = "GET",
                    urlRegex = "capture.me",
                    expiresIn = 10000
                )
            )
        )

        val DEFAULT_LOCAL_CONFIG = LocalConfig(
            appId = "CoYh3",
            ndkEnabled = false,
            sdkConfig = DEFAULT_SDK_LOCAL_CONFIG
        )
    }
}
