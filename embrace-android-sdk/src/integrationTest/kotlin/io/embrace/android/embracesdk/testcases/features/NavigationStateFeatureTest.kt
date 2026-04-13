package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.fakes.TestFragmentActivity
import io.embrace.android.embracesdk.fakes.TestNavControllerActivity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.POST_ACTIVITY_ACTION_DWELL
import io.embrace.android.embracesdk.testframework.actions.SessionPartTimestamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class NavigationStateFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val enabledConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            stateCaptureEnabled = true,
            bgActivityCapture = false,
        ),
    )

    private val enabledRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f)

    @Test
    fun `navigation state feature disabled when state capture flag off`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = false,
                ),
            ),
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                recordSession {}
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getNavigationStateSpan())
            },
        )
    }

    @Test
    fun `navigation state feature disabled when navigation flag off`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                ),
            ),
            persistedRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 0.0f),
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
            instrumentedConfig = enabledConfig,
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
                stateSpan1.assertStateSpan(
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val stateSpan2 = checkNotNull(sessionPayloads[1].getNavigationStateSpan())
                checkNotNull(secondSessionTimestamps)
                stateSpan2.assertStateSpan(
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
                    stateCaptureEnabled = true,
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
                baStateSpan1.assertStateSpan(
                    isForeground = false
                )

                val sessionStateSpan1 = checkNotNull(sessionPayloads[0].getNavigationStateSpan())
                checkNotNull(firstSessionTimestamps)
                sessionStateSpan1.assertStateSpan(
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val baStateSpan2 = checkNotNull(baPayloads[1].getNavigationStateSpan())
                baStateSpan2.assertStateSpan(
                    stateUninitialized = false,
                    isForeground = false
                )

                val sessionStateSpan2 = checkNotNull(sessionPayloads[1].getNavigationStateSpan())
                sessionStateSpan2.assertStateSpan(
                    stateUninitialized = false,
                    transitionTimesMs = listOf(checkNotNull(secondSessionTimestamps).startTimeMs, secondSessionTimestamps.endTimeMs),
                    newStateValues = listOf(loadedActivities.last().get().localClassName)
                )
            },
        )
    }

    @Test
    fun `FragmentActivity navigation recorded as state span`() {
        val navRoutes = listOf("contacts", "about", "home")
        var timestamps: AppExecutionTimestamps? = null

        testRule.runTest(
            instrumentedConfig = enabledConfig,
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateFragmentActivityNavigation(routes = navRoutes)
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                val eventTimes = mutableListOf(
                    timestamps.firstForegroundTimeMs,
                    timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL,
                    timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 2,
                    timestamps.firstActionTimeMs + POST_ACTIVITY_ACTION_DWELL * 3,
                    timestamps.lastBackgroundTimeMs,
                )
                val expectedRoutes = listOf("home") + navRoutes + listOf("Backgrounded")
                stateSpan.assertStateSpan(
                    transitionTimesMs = eventTimes,
                    newStateValues = expectedRoutes
                )
            },
        )
    }

    @Test
    fun `NavController destination restored when same Activity returns from background`() {
        val navActivity = Robolectric.buildActivity(TestFragmentActivity::class.java)
        testRule.runTest(
            instrumentedConfig = enabledConfig,
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                simulateFragmentActivityNavigation(
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
        val navActivity = Robolectric.buildActivity(TestFragmentActivity::class.java)
        var navigationTime: Long = 0
        testRule.runTest(
            instrumentedConfig = enabledConfig,
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
                stateSpan.assertStateSpan(
                    transitionTimesMs = listOf(
                        timestamps.firstForegroundTimeMs,
                        navigationTime - POST_ACTIVITY_ACTION_DWELL - 2 * LIFECYCLE_EVENT_GAP,
                        navigationTime,
                        timestamps.lastBackgroundTimeMs
                    ),
                    newStateValues = listOf(
                        startupActivity.get().localClassName,
                        "home",
                        "about",
                        "Backgrounded"
                    )
                )
            },
        )
    }

    @Test
    fun `navigation of NavController tracked using internal API creates state span`() {
        var timestamps: AppExecutionTimestamps? = null
        val activityController = Robolectric.buildActivity(TestNavControllerActivity::class.java)
        val expectedStateValues = listOf("home", "contacts", "about", "Backgrounded")
        testRule.runTest(
            instrumentedConfig = enabledConfig,
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                timestamps = simulateNavControllerTrackingAndNavigation(
                    routes = listOf("contacts", "about"),
                    activityController = activityController,
                )
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getNavigationStateSpan())
                checkNotNull(timestamps)
                val expectedTransitionTimes = listOf(
                    timestamps.firstForegroundTimeMs,
                    timestamps.firstForegroundTimeMs + POST_ACTIVITY_ACTION_DWELL + LIFECYCLE_EVENT_GAP * 2,
                    timestamps.firstForegroundTimeMs + POST_ACTIVITY_ACTION_DWELL * 2 + LIFECYCLE_EVENT_GAP * 2,
                    timestamps.lastBackgroundTimeMs,
                )
                stateSpan.assertStateSpan(
                    transitionTimesMs = expectedTransitionTimes,
                    newStateValues = expectedStateValues
                )
            },
        )
    }

    private fun Span.assertStateSpan(
        stateUninitialized: Boolean = true,
        isForeground: Boolean = true,
        transitionTimesMs: List<Long> = listOf(),
        newStateValues: List<String> = listOf(),
    ) {
        val startStateValue = if (stateUninitialized) {
            "Initializing"
        } else {
            "Backgrounded"
        }
        assertTrue(hasEmbraceAttributeValue(EMB_STATE_INITIAL_VALUE, startStateValue))

        with(checkNotNull(events)) {
            if (isForeground) {
                assertEquals(transitionTimesMs.size, size)
                (0..<transitionTimesMs.size - 1).forEach {
                    this[it].assertStateTransition(
                        timestampMs = transitionTimesMs[it],
                        newStateValue = newStateValues[it],
                    )
                }
                last().assertStateTransition(
                    timestampMs = transitionTimesMs.last(),
                    newStateValue = "Backgrounded",
                )
            } else {
                assertEquals(0, size)
            }
        }
    }

    private fun Envelope<SessionPartPayload>.getNavigationStateSpan() = getStateSpan("emb-state-screen-automatic")

    class HomeActivity : Activity()
    class SettingsActivity : Activity()
    class ProfileActivity : Activity()
}
