package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.attrs.embStateInitialValue
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationStateDataSource
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.AppExecutionTimestamps
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import io.embrace.android.embracesdk.testframework.actions.SessionTimestamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

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
        var throwable: Throwable? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = false,
                ),
            ),
            persistedRemoteConfig = enabledRemoteConfig,
            testCaseAction = {
                try {
                    findDataSource<NavigationStateDataSource>()
                } catch (e: IllegalStateException) {
                    throwable = e
                }
                recordSession {}
            },
            assertAction = {
                val navigationStateSpans = getSingleSessionEnvelope()
                    .findSpansOfType(EmbType.State)
                    .filter { it.name == "emb-state-screen-automatic" }
                assertEquals(0, navigationStateSpans.size)
                assertTrue(throwable is IllegalStateException)
            },
        )
    }

    @Test
    fun `navigation state feature disabled when navigation flag off`() {
        var throwable: Throwable? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                ),
            ),
            persistedRemoteConfig = RemoteConfig(pctNavigationStateCaptureEnabled = 0.0f),
            testCaseAction = {
                try {
                    findDataSource<NavigationStateDataSource>()
                } catch (e: IllegalStateException) {
                    throwable = e
                }
                recordSession {}
            },
            assertAction = {
                val navigationStateSpans = getSingleSessionEnvelope()
                    .findSpansOfType(EmbType.State)
                    .filter { it.name == "emb-state-screen-automatic" }
                assertEquals(0, navigationStateSpans.size)
                assertTrue(throwable is IllegalStateException)
            },
        )
    }

    @Test
    fun `activity navigation transitions recorded as state span`() {
        var firstSessionTimestamps: AppExecutionTimestamps? = null
        var secondSessionTimestamps: SessionTimestamps? = null
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
                val stateSpan1 = checkNotNull(sessionPayloads[0].getStateSpan("emb-state-screen-automatic"))
                checkNotNull(firstSessionTimestamps)
                stateSpan1.assertStateSpan(
                    activityLoaded = false,
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val stateSpan2 = checkNotNull(sessionPayloads[1].getStateSpan("emb-state-screen-automatic"))
                stateSpan2.assertStateSpan(
                    transitionTimesMs = listOf(checkNotNull(secondSessionTimestamps).startTimeMs, secondSessionTimestamps.endTimeMs),
                    newStateValues = listOf(loadedActivities.last().get().localClassName)
                )
            },
        )
    }

    @Test
    fun `activity navigation transitions recorded as state span when background activity enabled`() {
        var firstSessionTimestamps: AppExecutionTimestamps? = null
        var secondSessionTimestamps: SessionTimestamps? = null
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

                val baStateSpan1 = checkNotNull(baPayloads[0].getStateSpan("emb-state-screen-automatic"))
                baStateSpan1.assertStateSpan(
                    activityLoaded = false,
                    isForeground = false
                )

                val sessionStateSpan1 = checkNotNull(sessionPayloads[0].getStateSpan("emb-state-screen-automatic"))
                checkNotNull(firstSessionTimestamps)
                sessionStateSpan1.assertStateSpan(
                    activityLoaded = false,
                    transitionTimesMs = foregroundTimes.map { it - LIFECYCLE_EVENT_GAP * 2 } + firstSessionTimestamps.lastBackgroundTimeMs,
                    newStateValues = loadedActivities.map { it.get().localClassName }
                )

                val baStateSpan2 = checkNotNull(baPayloads[1].getStateSpan("emb-state-screen-automatic"))
                baStateSpan2.assertStateSpan(
                    isForeground = false
                )

                val sessionStateSpan2 = checkNotNull(sessionPayloads[1].getStateSpan("emb-state-screen-automatic"))
                sessionStateSpan2.assertStateSpan(
                    transitionTimesMs = listOf(checkNotNull(secondSessionTimestamps).startTimeMs, secondSessionTimestamps.endTimeMs),
                    newStateValues = listOf(loadedActivities.last().get().localClassName)
                )
            },
        )
    }

    private fun Span.assertStateSpan(
        activityLoaded: Boolean = true,
        isForeground: Boolean = true,
        transitionTimesMs: List<Long> = listOf(),
        newStateValues: List<String> = listOf(),
    ) {
        val startStateValue = if (activityLoaded) {
            "Backgrounded"
        } else {
            "Initializing"
        }
        assertTrue(hasEmbraceAttributeValue(embStateInitialValue, startStateValue))

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

    private class HomeActivity : Activity()
    private class SettingsActivity : Activity()
    private class ProfileActivity : Activity()
}
