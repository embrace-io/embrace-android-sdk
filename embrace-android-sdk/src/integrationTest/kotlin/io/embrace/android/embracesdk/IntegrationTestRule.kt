@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.utils.Provider
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
    lateinit var bootstrapper: ModuleInitBootstrapper

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {
        harness = harnessSupplier.invoke()
        with(harness) {
            bootstrapper = ModuleInitBootstrapper(
                initModule = overriddenInitModule,
                openTelemetryModule = overriddenInitModule.openTelemetryModule,
                coreModuleSupplier = { _, _ -> overriddenCoreModule },
                workerThreadModuleSupplier = { _ -> overriddenWorkerThreadModule },
                androidServicesModuleSupplier = { _, _, _ -> overriddenAndroidServicesModule },
                deliveryModuleSupplier = { _, _, _, _, _, _ -> overriddenDeliveryModule },
                anrModuleSupplier = { _, _, _, _ -> fakeAnrModule },
                nativeFeatureModuleSupplier = { _, _, _, _, _, _, _, _, _ -> fakeNativeFeatureModule }
            )
            val embraceImpl = EmbraceImpl(bootstrapper)
            Embrace.setImpl(embraceImpl)
            if (startImmediately) {
                embraceImpl.start(overriddenCoreModule.context, appFramework) {
                    overriddenConfigService.apply { appFramework = it }
                }
            }
        }
    }

    /**
     * Teardown the Embrace SDK, closing any resources as required
     */
    override fun after() {
        Embrace.getImpl().stop()
    }

    @Suppress("DEPRECATION")
    fun startSdk(
        context: Context = harness.overriddenCoreModule.context,
        appFramework: AppFramework = harness.appFramework,
        configServiceProvider: (framework: io.embrace.android.embracesdk.internal.payload.AppFramework) -> ConfigService = { harness.overriddenConfigService }
    ) {
        Embrace.getImpl().start(context, appFramework, configServiceProvider)
    }

    fun stopSdk() {
        Embrace.getImpl().stop()
    }

    /**
     * Test harness for which an instance is generated each test run and provided to the test by the Rule
     */
    @Suppress("DEPRECATION")
    internal class Harness @JvmOverloads constructor(
        currentTimeMs: Long = DEFAULT_SDK_START_TIME_MS,
        val startImmediately: Boolean = true,
        val appFramework: AppFramework = AppFramework.NATIVE,
        val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
        val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock),
        val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
        val overriddenCoreModule: FakeCoreModule = FakeCoreModule(
            logger = overriddenInitModule.logger
        ),
        val overriddenWorkerThreadModule: WorkerThreadModule = createWorkerThreadModule(overriddenInitModule),
        val overriddenConfigService: FakeConfigService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
            networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true),
            autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(thermalStatusCaptureEnabled = false)
        ),
        val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
            initModule = overriddenInitModule,
            coreModule = overriddenCoreModule,
            workerThreadModule = overriddenWorkerThreadModule
        ),
        val overriddenDeliveryModule: FakeDeliveryModule =
            FakeDeliveryModule(
                deliveryService = FakeDeliveryService(),
            ),
        val fakeAnrModule: AnrModule = FakeAnrModule(),
        val fakeNativeFeatureModule: FakeNativeFeatureModule = FakeNativeFeatureModule()
    ) {
        fun logWebView(url: String) {
            Embrace.getImpl().logWebView(url)
        }
    }

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L
    }
}
