package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getLastSentBackgroundActivity
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class BreadcrumbFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `custom breadcrumb feature`() {
        with(testRule) {
            checkNotNull(harness.recordSession {
                embrace.addBreadcrumb("Hello, world!")
            }).assertBreadcrumbWithMessage("Hello, world!")
            embrace.addBreadcrumb("Bye, world!")
            harness.recordSession {
                checkNotNull(harness.getLastSentBackgroundActivity()).assertBreadcrumbWithMessage("Bye, world!")
            }
        }
    }

    private fun Envelope<SessionPayload>.assertBreadcrumbWithMessage(message: String) {
        assertEquals(message, findSessionSpan().findEventOfType(EmbType.System.Breadcrumb).attributes?.findAttributeValue("message"))
    }
}
