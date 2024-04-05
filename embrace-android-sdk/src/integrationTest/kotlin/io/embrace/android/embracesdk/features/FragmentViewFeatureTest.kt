package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.findSpanAttribute
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FragmentViewFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `fragment view feature`() {
        with(testRule) {
            var startTimeMs: Long = 0
            val message = checkNotNull(harness.recordSession {
                startTimeMs = harness.fakeClock.now()
                embrace.startView("MyView")
                harness.fakeClock.tick(1000L)
                embrace.startView("AnotherView")
                harness.fakeClock.tick(2000L)
                embrace.endView("MyView")
                embrace.endView("AnotherView")
            })

            val fragmentBreadcrumbs = message.findSpansOfType(EmbType.Ux.View)
            assertEquals(2, fragmentBreadcrumbs.size)

            val breadcrumb1 = fragmentBreadcrumbs[0]

            with(breadcrumb1) {
                assertEquals("MyView", findSpanAttribute("view.name"))
                assertEquals(startTimeMs, startTimeNanos.nanosToMillis())
                assertEquals(startTimeMs + 3000L, endTimeNanos.nanosToMillis())
            }

            val breadcrumb2 = fragmentBreadcrumbs[1]
            with(breadcrumb2) {
                assertEquals("AnotherView", findSpanAttribute("view.name"))
                assertEquals(startTimeMs + 1000L, startTimeNanos.nanosToMillis())
                assertEquals(startTimeMs + 3000L, endTimeNanos.nanosToMillis())
            }

        }
    }
}
