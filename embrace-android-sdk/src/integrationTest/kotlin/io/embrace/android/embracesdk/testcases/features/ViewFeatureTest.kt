package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewFeatureTest: RobolectricTest() {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `view feature`() {
        var startTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()
                    embrace.startView("MyView")
                    clock.tick(1000L)
                    embrace.startView("AnotherView")
                    clock.tick(2000L)
                    embrace.endView("MyView")
                    embrace.endView("AnotherView")
                }
            },
            assertAction = {
                val viewSpans = getSingleSessionEnvelope().findSpansOfType(EmbType.Ux.View)
                assertEquals(3, viewSpans.size)

                with(viewSpans.single { it.attributes?.findAttributeValue("view.name") == "MyView" }) {
                    assertEquals(startTimeMs, startTimeNanos?.nanosToMillis())
                    assertEquals(startTimeMs + 3000L, endTimeNanos?.nanosToMillis())
                }

                with(viewSpans.single { it.attributes?.findAttributeValue("view.name") == "AnotherView" }) {
                    assertEquals(startTimeMs + 1000L, startTimeNanos?.nanosToMillis())
                    assertEquals(startTimeMs + 3000L, endTimeNanos?.nanosToMillis())
                }

                assertNotNull(viewSpans.single { it.attributes?.findAttributeValue("view.name") == "android.app.Activity" })
            }
        )
    }
}
