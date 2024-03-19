package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CustomBreadcrumbFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `custom breadcrumb feature`() {
        with(testRule) {
            var captureTime: Long = 0
            val message = harness.recordSession {
                captureTime = harness.fakeClock.now()
                embrace.addBreadcrumb("Hello, world!")
            }
            val breadcrumb = checkNotNull(message?.breadcrumbs?.customBreadcrumbs?.single())
            assertEquals("Hello, world!", breadcrumb.message)
            assertEquals(captureTime, breadcrumb.getStartTime())
        }
    }
}
