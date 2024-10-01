package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PushNotificationApiTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `log push notification and data type`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
                }
            },
            assertAction = {
                val msg = harness.getSingleSession()
                msg.assertNotification("notif-data")
            }
        )
    }

    @Test
    fun `log push data type`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, true)
                }
            },
            assertAction = {
                val msg = harness.getSingleSession()
                msg.assertNotification("data")
            }
        )
    }

    @Test
    fun `log push notification no data type`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, false)
                }
            },
            assertAction = {
                val msg = harness.getSingleSession()
                msg.assertNotification("notif")
            }
        )
    }

    @Test
    fun `log push unknown type`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, false)
                }
            },
            assertAction = {
                val msg = harness.getSingleSession()
                msg.assertNotification("unknown")
            }
        )
    }

    @Test
    fun `log push notification with pii`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.breadcrumbBehavior = FakeBreadcrumbBehavior(
                    captureFcmPiiDataEnabled = true
                )
            },
            testCaseAction = {
                startSdk()
                recordSession {
                    embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
                }
            },
            assertAction = {
                val payload = harness.getSingleSession()
                val sessionSpan = payload.findSessionSpan()
                val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
                assertTrue(checkNotNull(event.timestampNanos) > 0)
                assertEquals(
                    mapOf(
                        EmbType.System.PushNotification.toEmbraceKeyValuePair(),
                        "notification.title" to "title",
                        "notification.type" to "notif-data",
                        "notification.body" to "body",
                        "notification.id" to "id",
                        "notification.from" to "from",
                        "notification.priority" to "1"
                    ),
                    event.attributes?.toMap()
                )
            }
        )
    }

    private fun Envelope<SessionPayload>.assertNotification(type: String) {
        val sessionSpan = findSessionSpan()
        val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
        assertTrue(checkNotNull(event.timestampNanos) > 0)
        assertEquals(
            mapOf(
                EmbType.System.PushNotification.toEmbraceKeyValuePair(),
                "notification.type" to type,
                "notification.id" to "id",
                "notification.priority" to "1"
            ),
            event.attributes?.toMap()
        )
    }
}