package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.fakes.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class ActivityFeatureTest {

    @Rule
    @JvmField
    val testRule = IntegrationTestRule { EmbraceSetupInterface(startImmediately = false) }

    @Test
    fun `automatically capture activities`() {
        var startTimeMs: Long = 0

        testRule.runTest(
            setupAction = {
                overriddenConfigService.breadcrumbBehavior = FakeBreadcrumbBehavior(
                    automaticActivityCaptureEnabled = true
                )
            },
            testCaseAction = {
                startSdk()
                recordSession() {
                    startTimeMs = clock.now()
                    simulateActivityLifecycle()
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val viewSpan = message.findSpanOfType(EmbType.Ux.View)

                viewSpan.attributes?.assertMatches {
                    "view.name" to "android.app.Activity"
                }

                with(viewSpan) {
                    assertEquals(startTimeMs, startTimeNanos?.nanosToMillis())
                    assertEquals(startTimeMs + 30000L, endTimeNanos?.nanosToMillis())
                }
            }
        )
    }
}