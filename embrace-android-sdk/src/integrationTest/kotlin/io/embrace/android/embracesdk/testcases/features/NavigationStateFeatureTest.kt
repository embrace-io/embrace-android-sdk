package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.snapshots.Snapshot.Companion.sendApplyNotifications
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.fakes.ActivityFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.BasicNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.ComposeNavHostActivity
import io.embrace.android.embracesdk.fakes.FragmentFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.HasBackStack
import io.embrace.android.embracesdk.fakes.HasNavController
import io.embrace.android.embracesdk.fakes.MultiBackStackNav3Activity
import io.embrace.android.embracesdk.fakes.Nav3ObservedActivity
import io.embrace.android.embracesdk.fakes.Nav3UnobservedActivity
import io.embrace.android.embracesdk.fakes.TestNavControllerActivity
import io.embrace.android.embracesdk.fakes.TypedBackStackNav3Activity
import io.embrace.android.embracesdk.fakes.ViewFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.WrappedContextComposeNavHostActivity
import io.embrace.android.embracesdk.fakes.WrappedContextNav3ComposeActivity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import io.embrace.android.embracesdk.testframework.actions.SessionPartTimestamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class NavigationStateFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val disabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 0.0f)
    private val enabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f)

    @Test
    fun `navigation state feature disabled when navigation flag off`() {
        testRule.runTest(
            persistedRemoteConfig = disabledRemoteConfig,
            testCaseAction = {
                recordSession {}
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getNavigationStateSpan())
            },
        )
    }

    @Test
    fun `navigation state feature disabled when state feature flag is off`() {
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig.copy(pctStateCaptureEnabledV2 = 0.0f),
            testCaseAction = {
                recordSession {}
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getNavigationStateSpan())
            },
        )
    }

    @Test
    fun `activity navigation transitions recorded as state span`() {
        var firstSessionTimestamps: AppExecutionTimestamps? = null
        var secondSessionTimestamps: SessionPartTimestamps? = null
        val foregroundTimes = mutableListOf<Long>()
        val loadedActivities = listOf(
            Robolectric.buildActivity(HomeActivity::class.java),
            Robolectric.buildActivity(SettingsActivity::class.java),
            Robolectric.buildActivity(ProfileActivity::class.java)
        )
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                firstSessionTimestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        loadedActivities[0] to {
                            foregroundTimes.add(clock.now())
                        },
                        loadedActivities[1] to {
                            foregroundTimes.add(clock.now())
                        },
                        loadedActivities[2] to {
                            foregroundTimes.add(clock.now())
                        }
                    )
                )
                secondSessionTimestamps = recordSession(activityClass = ProfileActivity::class.java)
            },
            assertAction = {
                val sessionPayloads = getSessionEnvelopes(2)
                val stateSpan1 = checkNotNull(sessionPayloads[0].getNavigationStateSpan())
                checkNotNull(firstSessionTimestamps)
                stateSpan1.assertNavigationStateSpan(
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val stateSpan2 = checkNotNull(sessionPayloads[1].getNavigationStateSpan())
                checkNotNull(secondSessionTimestamps)
                stateSpan2.assertNavigationStateSpan(
                    stateUninitialized = false,
                    transitionTimesMs = listOf(secondSessionTimestamps.startTimeMs, secondSessionTimestamps.endTimeMs),
                    newStateValues = listOf(loadedActivities.last().get().localClassName)
                )
            },
        )
    }

    @Test
    fun `activity navigation transitions recorded as state span when background activity enabled`() {
        var firstSessionTimestamps: AppExecutionTimestamps? = null
        var secondSessionTimestamps: SessionPartTimestamps? = null
        val foregroundTimes = mutableListOf<Long>()
        val loadedActivities = listOf(
            Robolectric.buildActivity(HomeActivity::class.java),
            Robolectric.buildActivity(SettingsActivity::class.java),
            Robolectric.buildActivity(ProfileActivity::class.java)
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                firstSessionTimestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        loadedActivities[0] to {
                            foregroundTimes.add(clock.now())
                        },
                        loadedActivities[1] to {
                            foregroundTimes.add(clock.now())
                        },
                        loadedActivities[2] to {
                            foregroundTimes.add(clock.now())
                        }
                    )
                )
                secondSessionTimestamps = recordSession(activityClass = ProfileActivity::class.java)
            },
            assertAction = {
                val sessionPayloads = getSessionEnvelopes(2)
                val baPayloads = getSessionEnvelopes(2, AppState.BACKGROUND)

                val baStateSpan1 = checkNotNull(baPayloads[0].getNavigationStateSpan())
                baStateSpan1.assertNavigationStateSpan()

                val sessionStateSpan1 = checkNotNull(sessionPayloads[0].getNavigationStateSpan())
                checkNotNull(firstSessionTimestamps)
                sessionStateSpan1.assertNavigationStateSpan(
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val baStateSpan2 = checkNotNull(baPayloads[1].getNavigationStateSpan())
                baStateSpan2.assertNavigationStateSpan(
                    stateUninitialized = false
                )

                val sessionStateSpan2 = checkNotNull(sessionPayloads[1].getNavigationStateSpan())
                sessionStateSpan2.assertNavigationStateSpan(
                    stateUninitialized = false,
                    transitionTimesMs = listOf(checkNotNull(secondSessionTimestamps).startTimeMs, secondSessionTimestamps.endTimeMs),
                    newStateValues = listOf(loadedActivities.last().get().localClassName)
                )
            },
        )
    }

    @Test
    fun `FragmentActivity navigation recorded as state span`() {
        runNavStateTest<BasicNavHostFragmentActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
        ) { routes, controller ->
            simulateNavControllerActivityNavigation(routes, controller)
        }
    }

    @Test
    fun `NavController destination restored when same Activity returns from background`() {
        val navActivity = Robolectric.buildActivity(BasicNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                simulateNavControllerActivityNavigation(
                    routes = listOf("about"),
                    activityController = navActivity
                )
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    createFirstActivity = false,
                    activitiesAndActions = listOf(
                        navActivity to {},
                    )
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
    fun `navigate from plain startup activity to NavController activity`() {
        var timestamps: AppExecutionTimestamps? = null
        val startupActivity = Robolectric.buildActivity(HomeActivity::class.java)
        val navActivity = Robolectric.buildActivity(BasicNavHostFragmentActivity::class.java)
        var navigationTime: Long = 0
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        startupActivity to {},
                        navActivity to {
                            navigationTime = clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navActivity.get().getNavController().navigate("about")
                        },
                    )
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                stateSpan.assertNavigationStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        navigationTime - POST_ACTIVITY_ACTION_DWELL - 2 * LIFECYCLE_EVENT_GAP,
                        navigationTime - POST_ACTIVITY_ACTION_DWELL - LIFECYCLE_EVENT_GAP,
                        navigationTime,
                        timestamps.lastBackgroundTimeMs
                    ),
                    newStateValues = listOf(
                        startupActivity.get().localClassName,
                        navActivity.get().localClassName,
                        "home",
                        "about",
                    )
                )
            },
        )
    }

    @Test
    fun `rememberObservedNavController registers created NavController and creates state span`() {
        runNavStateTest<ComposeNavHostActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
            timing = NavRegistrationTiming.AT_COMPOSITION,
        ) { routes, controller ->
            simulateNavControllerActivityNavigation(routes, controller)
        }
    }

    @Test
    fun `rememberObservedNavController finds Activity associated with NavController through wrapped LocalContext`() {
        runNavStateTest<WrappedContextComposeNavHostActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
            timing = NavRegistrationTiming.AT_COMPOSITION,
        ) { routes, controller ->
            simulateNavControllerActivityNavigation(routes, controller)
        }
    }

    @Test
    fun `navigation of NavController tracked using public observeNavigation API creates state span`() {
        runNavStateTest<TestNavControllerActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
        ) { routes, controller ->
            simulateNavControllerTrackingAndNavigation(routes, controller)
        }
    }

    @Test
    fun `observeNavigation works when NavController retrieved via findNavController on View`() {
        runNavStateTest<ViewFindNavControllerActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
        ) { routes, controller ->
            simulateNavControllerTrackingAndNavigation(routes, controller)
        }
    }

    @Test
    fun `observeNavigation works when NavController retrieved via findNavController on Activity`() {
        runNavStateTest<ActivityFindNavControllerActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
        ) { routes, controller ->
            simulateNavControllerTrackingAndNavigation(routes, controller)
        }
    }

    @Test
    fun `observeNavigation works when NavController retrieved from destination Fragment via findNavController`() {
        runNavStateTest<FragmentFindNavControllerActivity>(
            defaultDestination = "home",
            routes = listOf("contacts", "about"),
        ) { routes, controller ->
            controller.get().fragmentResumeCallback = { activity, navController ->
                embrace.observeNavigation(activity, navController)
            }
            simulateNavControllerActivityNavigation(routes, controller)
        }
    }

    @Test
    fun `navController not auto detected if Activity view hierarchy is not the expected structure`() {
        var timestamps: AppExecutionTimestamps? = null
        val navActivity = Robolectric.buildActivity(FragmentFindNavControllerActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateNavControllerActivityNavigation(
                    routes = listOf("home", "contacts", "about"),
                    activityController = navActivity
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
    fun `rememberObservedBackStack registers created back stack and creates state span`() {
        runBackStackNavStateTest<Nav3ObservedActivity>(routes = listOf("contacts", "about"))
    }

    @Test
    fun `rememberObservedBackStack finds Activity associated with back stack through wrapped LocalContext`() {
        runBackStackNavStateTest<WrappedContextNav3ComposeActivity>(routes = listOf("about", "contacts"))
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

    @Test
    fun `transitions capped at limit`() {
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                val activityController = Robolectric.buildActivity(BasicNavHostFragmentActivity::class.java)
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        activityController to {
                            val navController = activityController.get().getNavController()
                            repeat(1001) { i ->
                                navController.navigate(
                                    if (i % 2 == 0) {
                                        "about"
                                    } else {
                                        "contacts"
                                    }
                                )
                                clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            }
                        },
                    )
                )
            },
            assertAction = {
                val stateSpan = getSingleSessionEnvelope().getNavigationStateSpan()
                val events = checkNotNull(stateSpan?.events)
                assertEquals(1000, events.size)
            },
        )
    }

    private inline fun <reified T> runNavStateTest(
        defaultDestination: String,
        routes: List<String>,
        timing: NavRegistrationTiming = NavRegistrationTiming.AT_RESUME,
        crossinline navAction: EmbraceActionInterface.(List<String>, ActivityController<T>) -> AppExecutionTimestamps,
    ) where T : Activity, T : HasNavController {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(T::class.java)
        val expectedStateValues = listOf(activityController.get().localClassName, defaultDestination) + routes
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = navAction(routes, activityController)
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                with(checkNotNull(timestamps)) {
                    val defaultDestChangeTime = when (timing) {
                        NavRegistrationTiming.AT_RESUME -> firstForegroundTimeMs + LIFECYCLE_EVENT_GAP
                        NavRegistrationTiming.AT_COMPOSITION -> firstActionTimeMs
                    }
                    val navTimes = routes.indices.map { i ->
                        firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * (i + 1)
                    }
                    stateSpan.assertNavigationStateSpan(
                        transitionTimesMs = listOf(firstForegroundTimeMs, defaultDestChangeTime) + navTimes + lastBackgroundTimeMs,
                        newStateValues = expectedStateValues,
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

    /**
     * Enum that indicates when the NavController tracking is done during a test. Used to determine the time to use to validate the
     * state change caused by the default destination being loaded.
     */
    private enum class NavRegistrationTiming {
        /**
         * NavController tracking happens during the invocation of the Activity's onResume callback.
         */
        AT_RESUME,

        /**
         * NavController tracking happens after during the composition stage, i.e. after onResume is complete.
         */
        AT_COMPOSITION,
    }

    class HomeActivity : Activity()
    class SettingsActivity : Activity()
    class ProfileActivity : Activity()
}
