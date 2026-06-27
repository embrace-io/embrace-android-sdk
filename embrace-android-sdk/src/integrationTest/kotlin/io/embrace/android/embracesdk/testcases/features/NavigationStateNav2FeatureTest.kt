package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.fakes.ActivityFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.ArgTemplateNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.BasicNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.ComposeNavHostActivity
import io.embrace.android.embracesdk.fakes.DialogNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.FqcnRouteActivityNavHost
import io.embrace.android.embracesdk.fakes.FragmentFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.HasNavController
import io.embrace.android.embracesdk.fakes.NestedGraphNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.SerializableRouteNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.TestNavControllerActivity
import io.embrace.android.embracesdk.fakes.ViewFindNavControllerActivity
import io.embrace.android.embracesdk.fakes.WrappedContextComposeNavHostActivity
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
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
internal class NavigationStateNav2FeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val enabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f)

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
    fun `routes with argument templates use the template not the resolved value`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(ArgTemplateNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateNavControllerActivityNavigation(
                    routes = listOf("profile/123", "order/456/details"),
                    activityController = activityController,
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                stateSpan.assertNavigationStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 2,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(
                        activityController.get().localClassName,
                        "home",
                        "profile/{userId}",
                        "order/{orderId}/details",
                    ),
                )
            },
        )
    }

    @Test
    fun `dialog destinations tracked like fragment destinations`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(DialogNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateNavControllerActivityNavigation(
                    routes = listOf("confirm_dialog"),
                    activityController = activityController,
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                stateSpan.assertNavigationStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(activityController.get().localClassName, "home", "confirm_dialog"),
                )
            },
        )
    }

    @Test
    fun `nested graph destinations use inner destination route`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(NestedGraphNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateNavControllerActivityNavigation(
                    routes = listOf("general"),
                    activityController = activityController,
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                stateSpan.assertNavigationStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(activityController.get().localClassName, "home", "general"),
                )
            },
        )
    }

    @Test
    fun `back navigation triggers destination change`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(BasicNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        activityController to {
                            val navController = activityController.get().getNavController()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.navigate("about")
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.navigate("contacts")
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.popBackStack()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.popBackStack()
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
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 2,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 3,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 4,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(
                        activityController.get().localClassName,
                        "home",
                        "about",
                        "contacts",
                        "about",
                        "home",
                    ),
                )
            },
        )
    }

    @Test
    fun `Serializable routes with SerialName use the serial name template not resolved args`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(SerializableRouteNavHostFragmentActivity::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        activityController to {
                            val navController = activityController.get().getNavController()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.navigate(SerializableRouteNavHostFragmentActivity.TypeSafeProfile(userId = "user_42"))
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
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(activityController.get().localClassName, "home", "profile/{userId}"),
                )
            },
        )
    }

    @Test
    fun `Serializable routes without SerialName use fully qualified class name with template not resolved as state value`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(FqcnRouteActivityNavHost::class.java)
        testRule.runTest(
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        activityController to {
                            val navController = activityController.get().getNavController()
                            clock.tick(POST_ACTIVITY_ACTION_DWELL)
                            navController.navigate(FqcnRouteActivityNavHost.FqcnDetail(itemId = 42))
                        },
                    )
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                val fqcnHome = checkNotNull(FqcnRouteActivityNavHost.FqcnHome::class.qualifiedName)
                val fqcnDetail = "${checkNotNull(FqcnRouteActivityNavHost.FqcnDetail::class.qualifiedName)}/{itemId}"
                stateSpan.assertNavigationStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        timestamps.firstForegroundTimeMs + LIFECYCLE_EVENT_GAP,
                        timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                        timestamps.lastBackgroundTimeMs,
                    ),
                    newStateValues = listOf(activityController.get().localClassName, fqcnHome, fqcnDetail),
                )
            },
        )
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
        val startupActivity = Robolectric.buildActivity(Activity::class.java)
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
}
