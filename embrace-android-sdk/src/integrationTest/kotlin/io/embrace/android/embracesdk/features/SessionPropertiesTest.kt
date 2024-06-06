package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            val session1 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty("perm", "permVal", true)
                embrace.addSessionProperty("temp", "tempVal", false)
            })

            val spans = checkNotNull(session1.spans)
            with(checkNotNull(spans.find { it.hasFixedAttribute(EmbType.Ux.Session) })) {
                assertEquals("thurr", getSessionProperty("always"))
                assertEquals("permVal", getSessionProperty("perm"))
                assertEquals("tempVal", getSessionProperty("temp"))
            }

            val session2 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty("newTemp", "value", false)
                embrace.removeSessionProperty("perm")
            })

            val spans2 = checkNotNull(session2.spans)
            with(checkNotNull(spans2.find { it.hasFixedAttribute(EmbType.Ux.Session) })) {
                assertEquals("thurr", getSessionProperty("always"))
                assertEquals("value", getSessionProperty("newTemp"))
                assertNull(getSessionProperty("perm"))
                assertNull(getSessionProperty("temp"))
            }

            val attributesFromSdk = checkNotNull(embrace.getSessionProperties())
            assertEquals("thurr", attributesFromSdk["always"])
            assertNull(attributesFromSdk["perm"])
            assertNull(attributesFromSdk["temp"])
            assertNull(attributesFromSdk["newTemp"])
        }
    }
}