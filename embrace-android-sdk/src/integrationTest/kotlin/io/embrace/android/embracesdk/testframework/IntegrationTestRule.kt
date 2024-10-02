package io.embrace.android.embracesdk.testframework

import android.content.Context
import io.embrace.android.embracesdk.EmbraceHooks
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleImpl
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
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
    private val embraceSetupInterfaceSupplier: Provider<EmbraceSetupInterface> = { EmbraceSetupInterface() }
) : ExternalResource() {

    /**
     * Used to perform actions on the Embrace class under test
     */
    private lateinit var action: EmbraceActionInterface

    /**
     * Used to perform actions on the output generated by a test
     */
    private lateinit var assertion: EmbraceAssertionInterface

    /**
     * Instance of the test harness that is recreating on every test iteration
     */
    lateinit var setup: EmbraceSetupInterface
    lateinit var bootstrapper: ModuleInitBootstrapper

    fun getEmbrace() = action.embrace

    /**
     * Runs a test case. The test case should be separated into 3 parts: setup, action, and
     * assertions. This aims to enforce the better compartmentalisation & reuse of test code within
     * the integration test suite.
     */
    inline fun runTest(
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        testCaseAction: EmbraceActionInterface.() -> Unit,
        assertAction: EmbraceAssertionInterface.() -> Unit = {},
    ) {
        setupAction(setup)
        testCaseAction(action)
        assertAction(assertion)
    }

    /**
     * Setup the Embrace SDK so it's ready for testing.
     */
    override fun before() {
        setup = embraceSetupInterfaceSupplier.invoke()
        bootstrapper = setup.createBootstrapper()
        action = EmbraceActionInterface(setup, bootstrapper)
        assertion = EmbraceAssertionInterface(bootstrapper)

        with(setup) {
            val embraceImpl = EmbraceImpl(bootstrapper)
            EmbraceHooks.setImpl(embraceImpl)
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
        EmbraceHooks.stop()
    }

    companion object {
        const val DEFAULT_SDK_START_TIME_MS = 169220160000L
    }
}
