@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk

import android.app.Activity
import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.FakePayloadStore
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
import org.robolectric.Robolectric

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
     * Used to perform actions on the Embrace class under test
     */
    lateinit var action: EmbraceActionInterface

    /**
     * Instance of the test harness that is recreating on every test iteration
     */
    lateinit var harness: Harness
    lateinit var bootstrapper: ModuleInitBootstrapper

    /**
     * Runs a test case. The test case should be separated into 3 parts: setup, action, and
     * assertions. This aims to enforce the better compartmentalisation & reuse of test code within
     * the integration test suite.
     */
    fun runTest(
        setupAction: Harness.() -> Unit = {},
        testCaseAction: EmbraceActionInterface.() -> Unit,
        assertAction: IntegrationTestRule.() -> Unit,
    ) {
        setupAction(harness)
        testCaseAction(action)
        assertAction(this)
    }

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {
        harness = harnessSupplier.invoke()
        action = EmbraceActionInterface(harness)
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

    /**
     * Interface for performing actions on the [Embrace] instance under test
     */
    internal class EmbraceActionInterface(private val harness: Harness) {

        /**
         * The [Embrace] instance that can be used for testing
         */
        val embrace = Embrace.getInstance()

        val clock: FakeClock
            get() = harness.overriddenClock

        val configService: FakeConfigService
            get() = harness.overriddenConfigService

        fun stopSdk() {
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

        /**
         * Starts & ends a session for the purposes of testing. An action can be supplied as a lambda
         * parameter: any code inside the lambda will be executed, so can be used to add breadcrumbs,
         * send log messages etc, while the session is active. The end session message is returned so
         * that the caller can perform further assertions if needed.
         *
         * This function fakes the lifecycle events that trigger a session start & end. The session
         * should always be 30s long. Additionally, it performs assertions against fields that
         * are guaranteed not to change in the start/end message.
         */
        internal fun recordSession(
            simulateActivityCreation: Boolean = false,
            action: EmbraceActionInterface.() -> Unit = {}
        ) {
            // get the activity service & simulate the lifecycle event that triggers a new session.
            val activityService = checkNotNull(Embrace.getImpl().activityService)
            val activityController =
                if (simulateActivityCreation) Robolectric.buildActivity(Activity::class.java) else null

            activityController?.create()
            activityController?.start()
            activityService.onForeground()
            activityController?.resume()

            // perform a custom action during the session boundary, e.g. adding a breadcrumb.
            action()

            // end session 30s later by entering background
            harness.overriddenClock.tick(30000)
            activityController?.pause()
            activityController?.stop()
            activityService.onBackground()
        }
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
        private val deliveryService: FakeDeliveryService = FakeDeliveryService(),
        val overriddenDeliveryModule: FakeDeliveryModule =
            FakeDeliveryModule(
                deliveryService = deliveryService,
                payloadStore = FakePayloadStore(deliveryService)
            ),
        val fakeAnrModule: AnrModule = FakeAnrModule(),
        val fakeNativeFeatureModule: FakeNativeFeatureModule = FakeNativeFeatureModule()
    ) {
    }

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L
    }
}
