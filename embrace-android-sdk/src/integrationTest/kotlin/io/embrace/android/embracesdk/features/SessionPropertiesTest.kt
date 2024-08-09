package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
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

            val spans = checkNotNull(session1.data.spans)
            with(checkNotNull(spans.find { it.hasFixedAttribute(EmbType.Ux.Session) })) {
                assertEquals("thurr", getSessionProperty("always"))
                assertEquals("permVal", getSessionProperty("perm"))
                assertEquals("tempVal", getSessionProperty("temp"))
            }

            val session2 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty("newTemp", "value", false)
                embrace.removeSessionProperty("perm")
            })

            val spans2 = checkNotNull(session2.data.spans)
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

    @Test
    fun `temp properties are cleared in next session`() {
        val permKey = "perm"
        val tempKey = "temp"
        val permVal = "permVal"
        val tempVal = "tempVal"
        with(testRule) {
            embrace.addSessionProperty(permKey, permVal, true)
            val firstSession = checkNotNull(harness.recordSession {
                embrace.addSessionProperty(tempKey, tempVal, false)
            })
            val secondSession = checkNotNull(harness.recordSession())
            val bgActivities = harness.getSentBackgroundActivities()
            assertEquals(2, bgActivities.size)
            val firstBg = bgActivities.first()
            val secondBg = bgActivities.last()

            // check perm property is in all payloads
            assertEquals(permVal, firstBg.findSessionSpan().getSessionProperty(permKey))
            assertEquals(permVal, firstSession.findSessionSpan().getSessionProperty(permKey))
            assertEquals(permVal, secondBg.findSessionSpan().getSessionProperty(permKey))
            assertEquals(permVal, secondSession.findSessionSpan().getSessionProperty(permKey))

            // check temp property is only in first session payload
            assertNull(firstBg.findSessionSpan().getSessionProperty(tempKey))
            assertEquals(tempVal, firstSession.findSessionSpan().getSessionProperty(tempKey))
            assertNull(secondBg.findSessionSpan().getSessionProperty(tempKey))
            assertNull(secondSession.findSessionSpan().getSessionProperty(tempKey))
        }
    }
}