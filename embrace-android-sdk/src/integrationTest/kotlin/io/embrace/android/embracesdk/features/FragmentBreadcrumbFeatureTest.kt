package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
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
            val message = harness.recordSession {
                startTime = harness.fakeClock.now()
                embrace.startView("MyView")
                harness.fakeClock.tick(1000L)
                embrace.startView("AnotherView")
                harness.fakeClock.tick(2000L)
                embrace.endView("MyView")
                embrace.endView("AnotherView")
            }
            val fragmentBreadcrumbs = checkNotNull(message?.breadcrumbs?.fragmentBreadcrumbs).sortedBy { it.name }
            assertEquals(2, fragmentBreadcrumbs.size)

            val breadcrumb1 = fragmentBreadcrumbs[0]
            assertEquals("AnotherView", breadcrumb1.name)
            assertEquals(startTime + 1000, breadcrumb1.start)
            assertEquals(startTime + 3000, breadcrumb1.endTime)

            val breadcrumb2 = fragmentBreadcrumbs[1]
            assertEquals("MyView", breadcrumb2.name)
            assertEquals(startTime, breadcrumb2.start)
            assertEquals(startTime + 3000, breadcrumb2.endTime)
        }
    }
}
