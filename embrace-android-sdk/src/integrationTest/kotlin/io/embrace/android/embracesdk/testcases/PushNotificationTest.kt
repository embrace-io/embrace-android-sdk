package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PushNotificationTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `log push notification and data type`() {
        with(testRule) {
            startSdk()
            val msg = checkNotNull(harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            })
            msg.assertNotification("notif-data")
        }
    }

    @Test
    fun `log push data type`() {
        with(testRule) {
            startSdk()
            val msg = checkNotNull(harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, true)
            })
            msg.assertNotification("data")
        }
    }

    @Test
    fun `log push notification no data type`() {
        with(testRule) {
            startSdk()
            val msg = checkNotNull(harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, false)
            })
            msg.assertNotification("notif")
        }
    }

    @Test
    fun `log push unknown type`() {
        with(testRule) {
            startSdk()
            val msg = checkNotNull(harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, false)
            })
            msg.assertNotification("unknown")
        }
    }

    @Test
    fun `log push notification with pii`() {
        with(testRule) {
            harness.overriddenConfigService.breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = { SdkLocalConfig(captureFcmPiiData = true) }
            )
            startSdk()
            val payload = checkNotNull(harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            })
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