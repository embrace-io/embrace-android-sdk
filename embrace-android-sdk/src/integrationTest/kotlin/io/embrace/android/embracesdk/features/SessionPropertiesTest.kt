package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionPropertiesTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session properties`() {
        with(testRule) {
            embrace.addSessionProperty("always", "thurr", true)
            val session = checkNotNull(harness.recordSession {
                embrace.addSessionProperty("perm", "permVal", true)
                embrace.addSessionProperty("temp", "tempVal", false)
            })

            val sessionSpan = checkNotNull(session.spans?.find { it.hasFixedAttribute(EmbType.Ux.Session) })
            assertEquals("thurr", sessionSpan.attributes["always"])
            assertEquals("permVal", sessionSpan.attributes["perm"])
            assertEquals("tempVal", sessionSpan.attributes["temp"])
        }
    }
}