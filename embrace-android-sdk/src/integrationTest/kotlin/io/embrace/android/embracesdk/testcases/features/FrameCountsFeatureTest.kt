package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import android.os.Build
import android.view.FrameMetrics
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbFrameCountsAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.FrameMetricsBuilder

/**
 * Frames rendered are counted by the vitals instrumentation and recorded in total on the foreground session part.
 *
 * Runs on API 30, where the jank budget is the 60Hz refresh interval (16.67ms) times the default 2x multiplier: Robolectric's
 * [FrameMetricsBuilder] can't set the API 31+ `DEADLINE`.
 */
@Config(sdk = [Build.VERSION_CODES.R])
@RunWith(AndroidJUnit4::class)
internal class FrameCountsFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val remoteConfig = RemoteConfig(pctSmoothnessEnabled = 100.0f)

    @Test
    fun `frame counts are recorded per foreground session part`() {
        val activities = listOf(
            Robolectric.buildActivity(HomeActivity::class.java),
            Robolectric.buildActivity(SettingsActivity::class.java),
            Robolectric.buildActivity(ProfileActivity::class.java),
        )
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    activitiesAndActions = listOf(
                        // the 45ms frame spans ceil(45 / 16.67) = 3 vsyncs, 2 dropped; expected 1 + 1 + 3 = 5
                        activities[0] to { activities[0].get().renderFrames(5, 5, 45) },
                        // 0 dropped; expected 1
                        activities[1] to { activities[1].get().renderFrames(5) },
                        // the 60ms frame spans ceil(60 / 16.67) = 4 vsyncs, 3 dropped; expected 4
                        activities[2] to { activities[2].get().renderFrames(60) },
                    ),
                )
            },
            assertAction = {
                val session = getSessionEnvelopes(1).single()
                val sessionAttrs = checkNotNull(session.findSessionPartSpan().attributes)
                assertEquals("5", sessionAttrs.findAttributeValue(EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES))
                assertEquals("10", sessionAttrs.findAttributeValue(EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES))
            },
        )
    }

    /** Delivers one frame per entry to the Activity's frame-metrics listeners, each taking that many milliseconds to render. */
    private fun Activity.renderFrames(vararg durationsMs: Long) {
        durationsMs.forEach { durationMs ->
            val frame = FrameMetricsBuilder()
                .setMetric(FrameMetrics.DRAW_DURATION, durationMs * 1_000_000L)
                .build()
            shadowOf(window).reportOnFrameMetricsAvailable(frame)
        }
    }

    class HomeActivity : Activity()
    class SettingsActivity : Activity()
    class ProfileActivity : Activity()
}
