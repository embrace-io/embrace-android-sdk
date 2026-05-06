package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.snapshots.Snapshot.Companion.sendApplyNotifications
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.fakes.HasBackStack
import io.embrace.android.embracesdk.fakes.MultiBackStackNav3Activity
import io.embrace.android.embracesdk.fakes.Nav3ObservedActivity
import io.embrace.android.embracesdk.fakes.Nav3UnobservedActivity
import io.embrace.android.embracesdk.fakes.TypedBackStackNav3Activity
import io.embrace.android.embracesdk.fakes.WrappedContextNav3ComposeActivity
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class NavigationStateNav3FeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val enabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f)

    @Test
    fun `rememberObservedBackStack registers created back stack and creates state span`() {
        runBackStackNavStateTest<Nav3ObservedActivity>(
            routes = listOf("contacts", "about")
        )
    }

    @Test
    fun `rememberObservedBackStack finds Activity associated with back stack through wrapped LocalContext`() {
        runBackStackNavStateTest<WrappedContextNav3ComposeActivity>(
            routes = listOf("about", "contacts")
        )
    }

    @Test
    fun `rememberObservedBackStack records typed route via toString`() {
        runBackStackNavStateTest<TypedBackStackNav3Activity>(
            routes = listOf(TypedBackStackNav3Activity.Screen.Detail(42)),
        )
    }

    @Test
    fun `back stack not tracked if rememberObservedBackStack is not used`() {
        var timestamps: AppExecutionTimestamps? = null
        val navActivity = Robolectric.buildActivity(Nav3UnobservedActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateBackStackNavigation(
                    routes = listOf("contacts", "about"),
                    activityController = navActivity,
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                with(checkNotNull(timestamps)) {
                    stateSpan.assertNavigationStateSpan(
                        transitionTimesMs = listOf(firstForegroundTimeMs, lastBackgroundTimeMs),
                        newStateValues = listOf(navActivity.get().localClassName),
                    )
                }
            },
        )
    }

    @Test
    fun `back stack destination restored when same Activity returns from background`() {
        val navActivity = Robolectric.buildActivity(Nav3ObservedActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                simulateBackStackNavigation(
                    routes = listOf("about"),
                    activityController = navActivity,
                )
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    createFirstActivity = false,
                    activitiesAndActions = listOf(navActivity to {}),
                )
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val stateSpan = checkNotNull(sessions[1].getNavigationStateSpan())
                val events = checkNotNull(stateSpan.events)
                val stateValue = checkNotNull(events.first().attributes).single { it.key == "emb.state.new_value" }.data
                assertEquals("about", stateValue)
            },
        )
    }

    @Test
    fun `last destination of any back stack restored when Activity with multiple back stacks returns from background`() {
        val navActivity = Robolectric.buildActivity(MultiBackStackNav3Activity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        navActivity to {
                            navActivity.visible()
                            sendApplyNotifications()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navActivity.get().getPrimaryBackStack().add("primary-detail")
                            sendApplyNotifications()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navActivity.get().getSecondaryBackStack().add("secondary-detail")
                            sendApplyNotifications()
                        },
                    )
                )
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    createFirstActivity = false,
                    activitiesAndActions = listOf(navActivity to {}),
                )
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val stateSpan = checkNotNull(sessions[1].getNavigationStateSpan())
                val events = checkNotNull(stateSpan.events)
                val stateValue = checkNotNull(events.first().attributes).single { it.key == "emb.state.new_value" }.data
                assertEquals("secondary-detail", stateValue)
            },
        )
    }

    @Test
    fun `multiple back stacks per activity each emit destination changes independently`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(MultiBackStackNav3Activity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        activityController to {
                            activityController.visible()
                            sendApplyNotifications()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            activityController.get().getPrimaryBackStack().add("primary-detail")
                            sendApplyNotifications()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            activityController.get().getSecondaryBackStack().add("secondary-detail")
                            sendApplyNotifications()
                        }
                    )
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                with(checkNotNull(timestamps)) {
                    stateSpan.assertNavigationStateSpan(
                        transitionTimesMs = listOf(
                            firstForegroundTimeMs,
                            firstActionTimeMs,
                            firstActionTimeMs,
                            firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                            firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 2,
                            lastBackgroundTimeMs,
                        ),
                        newStateValues = listOf(
                            activityController.get().localClassName,
                            "primary-home",
                            "secondary-home",
                            "primary-detail",
                            "secondary-detail",
                        ),
                    )
                }
            },
        )
    }

    private inline fun <reified T> runBackStackNavStateTest(
        routes: List<Any>,
        crossinline navAction: EmbraceActionInterface.(List<Any>, ActivityController<T>) -> AppExecutionTimestamps =
            { r, c -> simulateBackStackNavigation(r, c) },
    ) where T : Activity, T : HasBackStack {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(T::class.java)
        val expectedStateValues = listOf(activityController.get().localClassName, "home") + routes.map { it.toString() }
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = { timestamps = navAction(routes, activityController) },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                with(checkNotNull(timestamps)) {
                    val routeTimes = routes.indices.map { i ->
                        firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * (i + 1)
                    }
                    stateSpan.assertNavigationStateSpan(
                        transitionTimesMs = listOf(firstForegroundTimeMs, firstActionTimeMs) + routeTimes + lastBackgroundTimeMs,
                        newStateValues = expectedStateValues,
                    )
                }
            },
        )
    }
}
