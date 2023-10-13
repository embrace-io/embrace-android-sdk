package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.fakeSdkModeBehavior
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.AndroidServicesModuleImpl
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.SystemServiceModule
import io.embrace.android.embracesdk.injection.SystemServiceModuleImpl
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
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
    private val harnessSupplier: () -> Harness = { Harness() }
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
                { initModule },
                { _, _ -> fakeCoreModule },
                { workerThreadModule },
                { _ -> systemServiceModule },
                { _, _, _ -> androidServicesModule },
                { _, _, _, _, _, _, _, _, _, _, _ -> essentialServiceModule },
                { _, _, _, _, _ -> dataCaptureServiceModule },
                { _, _, _, _, _ -> fakeDeliveryModule }
            )
            Embrace.setImpl(embraceImpl)
            if (startImmediately) {
                embrace.start(fakeCoreModule.context, enableIntegrationTesting, appFramework)
            }
        }
    }

    /**
     * Teardown the Embrace SDK, closing any resources as required
     */
    override fun after() {
        InternalStaticEmbraceLogger.logger.setToDefault()
        Embrace.getImpl().stop()
    }

    /**
     * Test harness for which an instance is generated each test run and provided to the test by the Rule
     */
    internal class Harness(
        currentTimeMs: Long = DEFAULT_SDK_START_TIME_MS,
        val fakeClock: FakeClock = FakeClock(currentTime = currentTimeMs),
        val enableIntegrationTesting: Boolean = false,
        val appFramework: Embrace.AppFramework = Embrace.AppFramework.NATIVE,
        val initModule: InitModule = FakeInitModule(clock = fakeClock),
        val fakeCoreModule: FakeCoreModule = FakeCoreModule(),
        val workerThreadModule: WorkerThreadModule = WorkerThreadModuleImpl(),
        val fakeConfigService: FakeConfigService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
            sdkModeBehavior = fakeSdkModeBehavior(
                isDebug = fakeCoreModule.isDebug,
                localCfg = { DEFAULT_LOCAL_CONFIG }
            ),
            networkBehavior = fakeNetworkBehavior(
                localCfg = { DEFAULT_SDK_LOCAL_CONFIG },
                remoteCfg = { DEFAULT_SDK_REMOTE_CONFIG }
            ),
            spansBehavior = fakeSpansBehavior {
                SpansRemoteConfig(pctEnabled = 100f)
            }
        ),
        val systemServiceModule: SystemServiceModule =
            SystemServiceModuleImpl(
                coreModule = fakeCoreModule
            ),
        val androidServicesModule: AndroidServicesModule = AndroidServicesModuleImpl(
            initModule = initModule,
            coreModule = fakeCoreModule,
            workerThreadModule = workerThreadModule,
        ),
        val essentialServiceModule: EssentialServiceModule =
            EssentialServiceModuleImpl(
                initModule = initModule,
                coreModule = fakeCoreModule,
                workerThreadModule = workerThreadModule,
                systemServiceModule = systemServiceModule,
                androidServicesModule = androidServicesModule,
                buildInfo = BuildInfo.fromResources(fakeCoreModule.resources, fakeCoreModule.context.packageName),
                customAppId = null,
                enableIntegrationTesting = enableIntegrationTesting,
                configStopAction = { Embrace.getImpl().stop() },
                configServiceProvider = { fakeConfigService }
            ),
        val dataCaptureServiceModule: DataCaptureServiceModule =
            DataCaptureServiceModuleImpl(
                initModule = initModule,
                coreModule = fakeCoreModule,
                systemServiceModule = systemServiceModule,
                essentialServiceModule = essentialServiceModule,
                workerThreadModule = workerThreadModule
            ),
        val fakeDeliveryModule: FakeDeliveryModule =
            FakeDeliveryModule(
                initModule = initModule,
                coreModule = fakeCoreModule,
                essentialServiceModule = essentialServiceModule,
                dataCaptureServiceModule = dataCaptureServiceModule,
                workerThreadModule = workerThreadModule
            ),
        val startImmediately: Boolean = true
    )

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 1692201600L

        fun newHarness(startImmediately: Boolean) = Harness(startImmediately = startImmediately)

        private val DEFAULT_SDK_LOCAL_CONFIG = SdkLocalConfig(
            networking = NetworkLocalConfig(
                enableNativeMonitoring = false
            ),
            betaFeaturesEnabled = false
        )

        private val DEFAULT_SDK_REMOTE_CONFIG = RemoteConfig(
            disabledUrlPatterns = setOf("dontlogmebro.pizza")
        )

        val DEFAULT_LOCAL_CONFIG = LocalConfig(
            appId = "CoYh3",
            ndkEnabled = false,
            sdkConfig = DEFAULT_SDK_LOCAL_CONFIG
        )
    }
}
