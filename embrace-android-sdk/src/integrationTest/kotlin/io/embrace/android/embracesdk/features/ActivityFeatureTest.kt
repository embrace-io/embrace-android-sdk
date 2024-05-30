package io.embrace.android.embracesdk.features

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.ViewLocalConfig
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findAttributeValue
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class ActivityFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `automatically capture activities`() {
        with(testRule) {
            harness.overriddenConfigService.breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = {
                    SdkLocalConfig(
                        viewConfig = ViewLocalConfig(enableAutomaticActivityCapture = true)
                    )
                }
            )
            startSdk()
            var startTimeMs: Long = 0
            val message = checkNotNull(harness.recordSession(simulateAppStartup = true) {
                startTimeMs = harness.overriddenClock.now()
            })

            val viewSpans = message.findSpansOfType(EmbType.Ux.View)
            Assert.assertEquals(1, viewSpans.size)

            val span1 = viewSpans[0]

            with(span1) {
                Assert.assertEquals("android.app.Activity", attributes.findAttributeValue("view.name"))
                Assert.assertEquals(startTimeMs, startTimeNanos.nanosToMillis())
                Assert.assertEquals(startTimeMs + 30000L, endTimeNanos.nanosToMillis())
            }
        }
    }
}