package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findEventOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class PushNotificationApiTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `log push notification and data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("notif-data")
            }
        )
    }

    @Test
    fun `log push data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, true)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("data")
            }
        )
    }

    @Test
    fun `log push notification no data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, false)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("notif")
            }
        )
    }

    @Test
    fun `log push unknown type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, false)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("unknown")
            }
        )
    }

    @Test
    fun `log push notification with pii`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(fcmPiiCapture = true),
            ),
            testCaseAction = {
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
                }
            },
            assertAction = {
                val payload = getSingleSessionEnvelope()
                val sessionSpan = payload.findSessionSpan()
                val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
                assertTrue(checkNotNull(event.timestampNanos) > 0)
                event.attributes?.assertMatches(mapOf(
                    EmbType.System.PushNotification.toEmbraceKeyValuePair(),
                    "notification.title" to "title",
                    "notification.type" to "notif-data",
                    "notification.body" to "body",
                    "notification.id" to "id",
                    "notification.from" to "from",
                    "notification.priority" to 1,
                ))
            }
        )
    }

    private fun Envelope<SessionPayload>.assertNotification(type: String) {
        val sessionSpan = findSessionSpan()
        val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
        assertTrue(checkNotNull(event.timestampNanos) > 0)
        event.attributes?.assertMatches(mapOf(
            EmbType.System.PushNotification.toEmbraceKeyValuePair(),
            "notification.type" to type,
            "notification.id" to "id",
            "notification.priority" to 1,
        ))
    }
}
