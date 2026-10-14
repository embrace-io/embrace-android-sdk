package io.embrace.android.embracesdk.internal.api.delegate

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Public API calls check `SdkCallChecker.started` and then read modules from the bootstrapper.
 * `disable()` on another thread can reset the graph to `UninitializedModuleGraph` between the
 * check and the read, which could throw.
 */
@RunWith(AndroidJUnit4::class)
internal class ApiDelegateStopRaceTest {

    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var sdkCallChecker: SdkCallChecker

    @Before
    fun setUp() {
        bootstrapper = ModuleInitBootstrapper(FakeInitModule())
        sdkCallChecker = SdkCallChecker(FakeInternalLogger(), FakeTelemetryService()).apply {
            started.set(true)
        }
    }

    @Test
    fun `user api`() = assertNoThrow {
        UserApiDelegate(bootstrapper, sdkCallChecker).setUserIdentifier("id")
    }

    @Test
    fun `user session api`() = assertNoThrow {
        UserSessionApiDelegate(bootstrapper, sdkCallChecker).addUserSessionProperty("key", "value", PropertyScope.USER_SESSION)
    }

    @Test
    fun `logs api`() = assertNoThrow {
        LogsApiDelegate(bootstrapper, sdkCallChecker).logInfo("message")
    }

    @Test
    fun `breadcrumb api`() = assertNoThrow {
        BreadcrumbApiDelegate(bootstrapper, sdkCallChecker).addBreadcrumb("crumb")
    }

    @Test
    fun `view tracking api`() = assertNoThrow {
        ViewTrackingApiDelegate(bootstrapper, sdkCallChecker).startView("view")
    }

    @Test
    fun `network request api`() = assertNoThrow {
        NetworkRequestApiDelegate(bootstrapper, sdkCallChecker).recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest("https://example.com", HttpMethod.GET, 0, 1, 0, 0, 200),
        )
    }

    @Test
    fun `instrumentation api`() = assertNoThrow {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        InstrumentationApiDelegate(bootstrapper, sdkCallChecker).activityLoaded(activity)
    }

    @Test
    fun `sdk state api`() = assertNoThrow {
        SdkStateApiDelegate(bootstrapper, sdkCallChecker).currentUserSessionId
    }

    @Test
    fun `experiment api`() = assertNoThrow {
        val delegate = ExperimentApiDelegate(bootstrapper, sdkCallChecker)
        delegate.trackExperiments(listOf(delegate.createExperiment("experiment", "variant", 0)))
    }

    @Test
    fun `application init end`() = assertNoThrow {
        EmbraceImpl(bootstrapper = bootstrapper, sdkCallChecker = sdkCallChecker).applicationInitEnd()
    }

    private fun assertNoThrow(action: () -> Unit) {
        try {
            action()
        } catch (exc: Throwable) {
            fail("Public API threw when racing disable(): $exc\n${exc.stackTraceToString()}")
        }
    }
}
