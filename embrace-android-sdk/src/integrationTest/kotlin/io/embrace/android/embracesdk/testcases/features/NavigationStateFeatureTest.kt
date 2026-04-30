package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertNavigationStateSpan
import io.embrace.android.embracesdk.assertions.getNavigationStateSpan
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.SessionPartTimestamps
import org.junit.Assert.assertNull
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

    class HomeActivity : Activity()
    class SettingsActivity : Activity()
    class ProfileActivity : Activity()
}
