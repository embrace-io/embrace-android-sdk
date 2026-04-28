package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.fakes.ArgTemplateNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.BasicNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.DialogNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.FqcnRouteActivityNavHost
import io.embrace.android.embracesdk.fakes.NestedGraphNavHostFragmentActivity
import io.embrace.android.embracesdk.fakes.SerializableRouteNavHostFragmentActivity
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class NavigationComplexDestinationAndRouteTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val enabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f)

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
                // destination.route returns the template, not the resolved values
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
                // Should see the inner destination route, not the nested graph route
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
                    // state should update to the previous destination when the nav stack is popped
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
                // Route uses @SerialName template with placeholder, not resolved arg value
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
                // FQCN routes use qualified class name + arg template placeholder
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
}
