package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.SchemaKeys
import io.embrace.android.embracesdk.findSpanAttribute
import io.embrace.android.embracesdk.findSpans
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FragmentBreadcrumbFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `fragment breadcrumb feature`() {
        with(testRule) {
            var startTime: Long = 0
            val message = checkNotNull(harness.recordSession {
                startTime = harness.fakeClock.now()
                embrace.startView("MyView")
                harness.fakeClock.tick(1000L)
                embrace.startView("AnotherView")
                harness.fakeClock.tick(2000L)
                embrace.endView("MyView")
                embrace.endView("AnotherView")
            })

            val fragmentBreadcrumbs = message.findSpans("emb-${SchemaKeys.VIEW_BREADCRUMB}")
            assertEquals(2, fragmentBreadcrumbs.size)

            val breadcrumb1 = fragmentBreadcrumbs[0]
            assertEquals("MyView", breadcrumb1.findSpanAttribute("view.name"))

            val breadcrumb2 = fragmentBreadcrumbs[1]
            assertEquals("AnotherView", breadcrumb2.findSpanAttribute("view.name"))
        }
    }
}
